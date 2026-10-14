"""The phone pipeline in plain Python, as the spec for the Kotlin port, checked against gliner2's own extract().

Usage: python -I eval/reference.py <model dir> <out dir> <texts.jsonl>...
1. Captures the fixed schema prefix (token ids + marker positions) from one official run; it never changes in the app.
2. Exports two ONNX graphs: the encoder (input_ids, attention_mask -> tokens; shipped int8) and the heads
   (tokens, word_index, word_mask -> candidate spans, logits, class logits; fp32, small).
3. Re-implements pre/post-processing around that graph (word split, per-word tokens, decode) and compares the result
   with extract() on every text in the given files, including the input ids.
Writes <out dir>/encoder(.int8).onnx, heads.onnx and spec.json (everything Kotlin needs besides the tokenizer).
"""

import json
import re
import sys
from pathlib import Path

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).parent))  # -I leaves the script's folder off the path
from run_eval import GLINER_CATEGORIES, GLINER_KINDS  # noqa: E402

ENTITIES = {
    "amount": "The transaction amount with its currency. Not the balance, fee, transaction cost or limit.",
    "merchant": "The business, service or person the money was paid to or received from.",
    "reference": "Transaction reference or ID code.",
}
THRESHOLD = 0.5  # extract()'s default for spans
# The boundary head specializes on the word count when traced, so words are padded to a fixed count and masked.
MAX_WORDS = 128
KINDS, CATEGORIES = list(GLINER_KINDS), list(GLINER_CATEGORIES)


def official_schema(model):
    return (model.create_schema().entities(ENTITIES).classification("kind", KINDS)
            .classification("category", CATEGORIES))


def capture_prefix(model):
    """Runs extract() once and records the batch: the prefix is everything before the first text word."""
    seen = {}
    orig = model._encode_core

    def spy(batch, hidden_states=None):
        seen["batch"] = batch
        return orig(batch, hidden_states)

    model._encode_core = spy
    model.extract("UJ80N9LJJV Confirmed. Ksh20.00 sent to SAFARICOM DATA", official_schema(model))
    model._encode_core = orig
    b = seen["batch"]
    first_word = int(b.text_word_indices[0, 0])
    return {
        "prefix_ids": b.input_ids[0, :first_word].tolist(),
        "query_index": b.query_marker_indices[0].tolist(),
        "class_index": b.cls_marker_indices[0].tolist(),
        "kinds": KINDS,
        "categories": CATEGORIES,
        "entities": list(ENTITIES),
        "span_threshold": THRESHOLD,
        "abstention_threshold": float(model.boundary_settings.abstention_threshold),
        "word_pattern": model.processor.word_splitter._PATTERN.pattern,
    }


class Heads(torch.nn.Module):
    def __init__(self, detector):
        super().__init__()
        self.detector = detector

    def forward(self, tokens, word_index, word_mask):
        return self.detector.heads(tokens, word_index, word_mask)


class Detector(torch.nn.Module):
    """Encoder plus heads for the fixed schema. Everything data-dependent stays in the graph; decode stays outside."""

    def __init__(self, model, spec):
        super().__init__()
        self.model = model
        self.register_buffer("query_index", torch.tensor([spec["query_index"]]))
        self.register_buffer("class_index", torch.tensor([spec["class_index"]]))
        self.n_kinds = len(spec["kinds"])

    def forward(self, input_ids, attention_mask, word_index, word_mask):
        tokens = self.model.encoder(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state
        return self.heads(tokens, word_index, word_mask)

    def heads(self, tokens, word_index, word_mask):
        """Everything after the encoder. Exported on its own so the encoder can be int8 and the heads stay fp32."""
        m = self.model
        h = tokens.shape[-1]

        def gather(index):
            return tokens.gather(1, index.unsqueeze(-1).expand(-1, -1, h))

        text = gather(word_index) * word_mask.unsqueeze(-1).to(tokens.dtype)
        queries = gather(self.query_index)
        query_mask = torch.ones(queries.shape[:2], dtype=torch.bool)
        out = m.boundary_head(text, word_mask, queries, query_mask, return_candidates=True)
        c = out.candidates
        temp = m.boundary_settings
        class_logits = m.classifier(gather(self.class_index)).squeeze(-1) / temp.classification_temperature
        return (
            c.indices,  # [1, Q, C, 2] word start, word end (half-open)
            c.pair_logits / temp.pair_temperature,  # [1, Q, C]
            c.valid_mask,  # [1, Q, C]
            out.null_logits,  # [1, Q] abstention
            class_logits[:, : self.n_kinds],
            class_logits[:, self.n_kinds:],
        )


def split_words(pattern, text):
    return [(m.group().lower(), m.start(), m.end()) for m in re.finditer(pattern, text, re.VERBOSE | re.IGNORECASE)]


def normalize(text):
    """extract() makes every text end in sentence punctuation before splitting it."""
    if not text:
        return "."
    return text if text.endswith((".", "!", "?")) else text + "."


def encode(spec, tokenizer, text):
    words = split_words(spec["word_pattern"], normalize(text))[:MAX_WORDS]
    ids = list(spec["prefix_ids"])
    word_index = []
    for word, _, _ in words:
        pieces = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(word))
        if not pieces:
            pieces = [tokenizer.unk_token_id]
        word_index.append(len(ids))
        ids.extend(pieces)
    return words, ids, word_index


def pad_words(word_index):
    """Pads to MAX_WORDS (index 0, masked out); longer messages are cut, as extract() does with max_len."""
    word_index = word_index[:MAX_WORDS]
    n = len(word_index)
    padded = torch.zeros(1, MAX_WORDS, dtype=torch.long)
    padded[0, :n] = torch.tensor(word_index)
    mask = torch.zeros(1, MAX_WORDS, dtype=torch.bool)
    mask[0, :n] = True
    return padded, mask


def best_flat(spans):
    """Maximum-total-score set of non-overlapping half-open spans (weighted interval scheduling)."""
    spans = sorted(set(spans), key=lambda s: (s[2], s[1]))
    best = [(0.0, [])]
    ends = [s[2] for s in spans]
    for i, (score, start, end) in enumerate(spans):
        j = np.searchsorted(ends, start, side="right")  # spans ending at or before this start
        take = (best[j][0] + score, best[j][1] + [spans[i]])
        best.append(max(best[-1], take, key=lambda t: t[0]))
    return sorted(best[-1][1], key=lambda s: (-s[0], s[1], s[2]))


def decode(spec, text, words, outputs):
    indices, logits, valid, null_logits, kind_logits, category_logits = (np.asarray(o) for o in outputs)
    probs = 1 / (1 + np.exp(-logits[0]))
    nulls = 1 / (1 + np.exp(-null_logits[0]))
    result = {"entities": {}}
    for q, name in enumerate(spec["entities"]):
        found = []
        if nulls[q] <= spec["abstention_threshold"]:
            # Same boundary twice: keep the higher score, as extract() does before resolving overlaps.
            by_bounds = {}
            for c in range(probs.shape[1]):
                if valid[0, q, c] and probs[q, c] >= spec["span_threshold"]:
                    s, e = int(indices[0, q, c, 0]), int(indices[0, q, c, 1])
                    if 0 <= s < e <= len(words):
                        by_bounds[(s, e)] = max(by_bounds.get((s, e), 0.0), float(probs[q, c]))
            for _, s, e in best_flat([(p, s, e) for (s, e), p in by_bounds.items()]):
                surface = text[words[s][1]:words[e - 1][2]].strip()
                if surface and surface not in found:
                    found.append(surface)
        result["entities"][name] = found
    result["kind"] = spec["kinds"][int(np.argmax(kind_logits[0]))]
    result["category"] = spec["categories"][int(np.argmax(category_logits[0]))]
    return result


def _without_stable(fn):
    def call(*args, stable=None, **kwargs):  # ONNX has no stable sort; order of exact ties doesn't change decode
        return fn(*args, **kwargs)

    return call


def export_heads(detector, path):
    """dynamo exporter: the legacy one can't trace the head's sorts. ONNX has no stable sort, so it's dropped here."""
    sort, argsort = torch.sort, torch.argsort
    torch.sort, torch.argsort = _without_stable(sort), _without_stable(argsort)
    hidden = detector.model.encoder.config.hidden_size
    sample = (torch.randn(1, 180, hidden), *pad_words(list(range(160, 171))))
    try:
        with torch.no_grad():
            torch.onnx.export(
                Heads(detector), sample, str(path), opset_version=18, dynamo=True, external_data=False,
                input_names=["tokens", "word_index", "word_mask"],
                output_names=["span_index", "span_logits", "span_valid", "null_logits", "kind_logits",
                              "category_logits"],
                dynamic_axes={"tokens": {1: "seq"}, "span_index": {2: "cands"}, "span_logits": {2: "cands"},
                              "span_valid": {2: "cands"}},
            )
    finally:
        torch.sort, torch.argsort = sort, argsort


def main(model_dir, out_dir, *text_files):
    import onnxruntime as ort
    from gliner2 import AutoExtractor
    from onnx_encoder import main as export_encoder

    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    export_encoder(model_dir, out)  # encoder.onnx + encoder.int8.onnx
    model = AutoExtractor.from_pretrained(model_dir).eval()
    spec = capture_prefix(model)
    tokenizer = model.processor.tokenizer
    detector = Detector(model, spec).eval()
    export_heads(detector, out / "heads.onnx")
    model.eval()  # export can leave modules in training mode
    (out / "spec.json").write_text(json.dumps(spec, indent=1))

    def session(name):
        return ort.InferenceSession(str(out / name), providers=["CPUExecutionProvider"])

    heads = session("heads.onnx")
    encoders = {"fp32": session("encoder.onnx"), "int8": session("encoder.int8.onnx")}

    def run_onnx(encoder, feeds):
        (tokens,) = encoder.run(None, {"input_ids": feeds[0].numpy(), "attention_mask": feeds[1].numpy()})
        return heads.run(None, {"tokens": tokens, "word_index": feeds[2].numpy(), "word_mask": feeds[3].numpy()})

    texts = [json.loads(line)["text"] for f in text_files for line in open(f) if line.strip()]
    schema = official_schema(model)
    agree = {"ids": 0, "torch": 0, "onnx fp32": 0, "onnx int8 encoder": 0}
    shown = 0
    for text in texts:
        seen = {}
        orig = model._encode_core

        def spy(batch, hidden_states=None, _orig=orig):
            seen["ids"] = batch.input_ids[0][batch.attention_mask[0].bool()].tolist()
            return _orig(batch, hidden_states)

        model._encode_core = spy
        want = model.extract(text, schema)
        model._encode_core = orig
        want = {"entities": {k: list(v) for k, v in want["entities"].items()}, "kind": want["kind"],
                "category": want["category"]}

        words, ids, word_index = encode(spec, tokenizer, text)
        agree["ids"] += ids == seen["ids"]
        feeds = (torch.tensor([ids]), torch.ones(1, len(ids), dtype=torch.long), *pad_words(word_index))
        norm = normalize(text)
        with torch.no_grad():
            got = {"torch": decode(spec, norm, words, [o.numpy() for o in detector(*feeds)])}
        got["onnx fp32"] = decode(spec, norm, words, run_onnx(encoders["fp32"], feeds))
        got["onnx int8 encoder"] = decode(spec, norm, words, run_onnx(encoders["int8"], feeds))
        for name, result in got.items():
            agree[name] += result == want
            if result != want and name != "onnx int8 encoder" and shown < 6:
                shown += 1
                print(f"MISMATCH [{name}] {text[:70]!r}\n  want {want}\n  got  {result}")
    n = len(texts)
    print(f"\n{n} texts")
    for name, k in agree.items():
        print(f"  {name:<20} matches extract(): {k}/{n}")
    for f in ("encoder.int8.onnx", "heads.onnx"):
        print(f"  {f:<20} {(out / f).stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main(*sys.argv[1:])
