"""Writes golden cases for the Kotlin port: each stage's exact output for a sample of texts.

Usage: python -I eval/fixtures.py <detector dir> <out.jsonl> <texts.jsonl>...
<detector dir> holds encoder.int8.onnx, heads.onnx, spec.json and tokenizer.json (from reference.py). Each line has
the text, its words with offsets, input ids, word indices, the heads' raw outputs and the decoded result, so JVM
tests can check split, tokenize and decode without onnxruntime, and one device test can check the graphs.
"""

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))  # -I leaves the script's folder off the path
from reference import decode, normalize, split_words  # noqa: E402
from unigram import Unigram  # noqa: E402

MAX_WORDS = 128


def main(detector_dir, out_path, *text_files):
    import onnxruntime as ort

    d = Path(detector_dir)
    spec = json.loads((d / "spec.json").read_text())
    tok = Unigram(d / "tokenizer.json")
    encoder = ort.InferenceSession(str(d / "encoder.int8.onnx"), providers=["CPUExecutionProvider"])
    heads = ort.InferenceSession(str(d / "heads.onnx"), providers=["CPUExecutionProvider"])
    texts = [json.loads(line)["text"] for f in text_files for line in open(f) if line.strip()]
    with open(out_path, "w") as out:
        for text in texts:
            norm = normalize(text)
            words = split_words(spec["word_pattern"], norm)[:MAX_WORDS]
            ids, word_index = list(spec["prefix_ids"]), []
            for word, _, _ in words:
                word_index.append(len(ids))
                ids.extend(tok.encode_word(word))
            padded = np.zeros((1, MAX_WORDS), dtype=np.int64)
            padded[0, :len(word_index)] = word_index
            mask = np.zeros((1, MAX_WORDS), dtype=bool)
            mask[0, :len(word_index)] = True
            (tokens,) = encoder.run(None, {"input_ids": np.array([ids], dtype=np.int64),
                                           "attention_mask": np.ones((1, len(ids)), dtype=np.int64)})
            raw = heads.run(None, {"tokens": tokens, "word_index": padded, "word_mask": mask})
            names = ["span_index", "span_logits", "span_valid", "null_logits", "kind_logits", "category_logits"]
            out.write(json.dumps({
                "text": text,
                "words": [list(w) for w in words],
                "input_ids": ids[len(spec["prefix_ids"]):],  # the prefix is in spec.json
                "word_index": word_index,
                "heads": {k: v.tolist() for k, v in zip(names, raw)},
                "result": decode(spec, norm, words, raw),
            }, ensure_ascii=False) + "\n")
    print(f"wrote {len(texts)} cases to {out_path}")


if __name__ == "__main__":
    main(*sys.argv[1:])
