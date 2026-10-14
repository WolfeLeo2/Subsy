"""SentencePiece Unigram tokenization of one word from tokenizer.json, the spec for the Kotlin tokenizer.

Usage: python -I eval/unigram.py <tokenizer.json> <texts.jsonl>...
Checks every word of every text against the model's own tokenizer and prints mismatches.
"""

import json
import sys
import unicodedata
from pathlib import Path


class Unigram:
    def __init__(self, tokenizer_json):
        model = json.loads(Path(tokenizer_json).read_text())["model"]
        self.ids = {piece: i for i, (piece, _) in enumerate(model["vocab"])}
        self.scores = [score for _, score in model["vocab"]]
        self.unk = model["unk_id"]
        self.max_len = max(len(piece) for piece, _ in model["vocab"])

    def encode_word(self, word):
        """Best-scoring split of "▁" + word (Viterbi). Runs of characters no piece covers become one unk."""
        text = "▁" + unicodedata.normalize("NFC", word)
        n = len(text)
        unk_score = min(self.scores) - 10.0  # what the tokenizers library charges for an unknown character
        best = [(-float("inf"), -1, -1)] * (n + 1)  # (score, start, piece id) ending at i
        best[0] = (0.0, -1, -1)
        for end in range(1, n + 1):
            for start in range(max(0, end - self.max_len), end):
                if best[start][0] == -float("inf"):
                    continue
                piece_id = self.ids.get(text[start:end])
                if piece_id is not None:
                    score = best[start][0] + self.scores[piece_id]
                elif end - start == 1:
                    score, piece_id = best[start][0] + unk_score, self.unk
                else:
                    continue
                if score > best[end][0]:
                    best[end] = (score, start, piece_id)
        pieces, end = [], n
        while end > 0:
            _, start, piece_id = best[end]
            pieces.append(piece_id)
            end = start
        pieces.reverse()
        merged = []  # consecutive unknowns fuse into one, as the library does
        for p in pieces:
            if not (p == self.unk and merged and merged[-1] == self.unk):
                merged.append(p)
        return merged


def main(tokenizer_json, *text_files):
    import re

    from transformers import AutoTokenizer

    sys.path.insert(0, str(Path(__file__).parent))  # -I leaves the script's folder off the path
    from reference import normalize

    spec_pattern = json.loads((Path(tokenizer_json).parent / "spec.json").read_text())["word_pattern"] \
        if (Path(tokenizer_json).parent / "spec.json").exists() else None
    official = AutoTokenizer.from_pretrained(str(Path(tokenizer_json).parent))
    mine = Unigram(tokenizer_json)
    pattern = spec_pattern or sys.exit("put spec.json next to tokenizer.json")
    words = set()
    for f in text_files:
        for line in open(f):
            if line.strip():
                text = normalize(json.loads(line)["text"])
                words.update(m.group().lower() for m in re.finditer(pattern, text, re.VERBOSE | re.IGNORECASE))
    bad = 0
    for word in sorted(words):
        want = official.convert_tokens_to_ids(official.tokenize(word)) or [official.unk_token_id]
        got = mine.encode_word(word)
        if want != got:
            bad += 1
            if bad <= 8:
                print(f"MISMATCH {word!r}: want {official.convert_ids_to_tokens(want)} got {official.convert_ids_to_tokens(got)}")
    print(f"{len(words)} distinct words, {bad} mismatches")


if __name__ == "__main__":
    main(*sys.argv[1:])
