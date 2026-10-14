"""Writes what the app needs from eval/: the shipping vocab and the JVM test resources.

Usage: python -I eval/export_app.py <detector dir> <app test resources dir> <synthetic texts.jsonl>...
- <detector dir>/vocab.tsv: the tokenizer the app loads ("unk_id", "min_score" header rows, then id, piece, score).
- <test dir>/detection/spec.json: spec.json without the Python word pattern (the app owns its own splitter).
- <test dir>/detection/vocab-subset.tsv: the same format, only pieces that occur inside the test words. Viterbi only
  looks at substrings of the word, so results are identical to the full vocab.
- <test dir>/detection/tokens.jsonl: text, words with offsets and input ids for every synthetic text.
- copies <detector dir>/fixtures.jsonl (decode cases) next to them.
Only pass synthetic texts: the test resources get committed.
"""

import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))  # -I leaves the script's folder off the path
from reference import normalize, split_words  # noqa: E402
from unigram import Unigram  # noqa: E402

MAX_WORDS = 128


def write_vocab(path, vocab, unk_id, min_score, keep=None):
    with open(path, "w") as f:
        f.write(f"unk_id\t{unk_id}\nmin_score\t{min_score!r}\n")
        for i, (piece, score) in enumerate(vocab):
            if keep is None or piece in keep:
                assert "\t" not in piece and "\n" not in piece, piece
                f.write(f"{i}\t{piece}\t{score!r}\n")


def main(detector_dir, test_dir, *text_files):
    d, t = Path(detector_dir), Path(test_dir) / "detection"
    t.mkdir(parents=True, exist_ok=True)
    model = json.loads((d / "tokenizer.json").read_text())["model"]
    vocab, unk_id = model["vocab"], model["unk_id"]
    min_score = min(score for _, score in vocab)
    write_vocab(d / "vocab.tsv", vocab, unk_id, min_score)

    spec = json.loads((d / "spec.json").read_text())
    pattern = spec.pop("word_pattern")
    (t / "spec.json").write_text(json.dumps(spec))

    tok = Unigram(d / "tokenizer.json")
    texts = [json.loads(line)["text"] for f in text_files for line in open(f) if line.strip()]
    texts = list(dict.fromkeys(texts))
    used = set()
    with open(t / "tokens.jsonl", "w") as out:
        for text in texts:
            assert all(ord(c) <= 0xFFFF for c in text), "offsets differ between Python and Kotlin above the BMP"
            words = split_words(pattern, normalize(text))[:MAX_WORDS]
            ids = []
            for word, _, _ in words:
                ids.extend(tok.encode_word(word))
                marked = "▁" + word
                used.update(marked[i:j] for i in range(len(marked)) for j in range(i + 1, len(marked) + 1))
            out.write(json.dumps({"text": text, "words": [list(w) for w in words], "ids": ids},
                                 ensure_ascii=False) + "\n")
    write_vocab(t / "vocab-subset.tsv", vocab, unk_id, min_score, keep=used)
    shutil.copy(d / "fixtures.jsonl", t / "fixtures.jsonl")
    for f in sorted(t.iterdir()):
        print(f"{f}  {f.stat().st_size / 1e3:.0f} KB")
    print(f"{d / 'vocab.tsv'}  {(d / 'vocab.tsv').stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main(*sys.argv[1:])
