"""Fine-tunes a GLiNER2 checkpoint on eval/train.jsonl from make_train.py.

Usage: python -I eval/train_gliner.py <train.jsonl> <base model dir> <output dir> [epochs]
The best checkpoint lands in <output dir>/best; score it with run_eval.py.
"""

import json
import sys
from pathlib import Path

import torch
from gliner2 import AutoExtractor
from gliner2.training.data import Classification, InputExample, TrainingDataset
from gliner2.training.trainer import ExtractorTrainer, TrainingConfig

sys.path.insert(0, str(Path(__file__).parent))  # -I leaves the script's folder off the path
from run_eval import GLINER_CATEGORIES, GLINER_KINDS  # noqa: E402

DESCRIPTIONS = {
    "amount": "The transaction amount with its currency. Not the balance, fee, transaction cost or limit.",
    "merchant": "The business, service or person the money was paid to or received from.",
    "reference": "Transaction reference or ID code.",
}


def main(train_path, base, out):
    torch.set_num_threads(8)
    examples = []
    for line in open(train_path):
        row = json.loads(line)
        examples.append(InputExample(
            text=row["text"],
            entities={k: row["entities"].get(k, []) for k in DESCRIPTIONS},
            entity_descriptions=DESCRIPTIONS,
            classifications=[
                Classification(task="kind", labels=list(GLINER_KINDS), true_label=row["kind"]),
                Classification(task="category", labels=list(GLINER_CATEGORIES), true_label=row["category"]),
            ],
        ))
    dataset = TrainingDataset(examples)
    dataset.validate(raise_on_error=True)
    train, val, _ = dataset.split(train_ratio=0.85, val_ratio=0.15, test_ratio=0.0, shuffle=True, seed=42)

    model = AutoExtractor.from_pretrained(base)
    config = TrainingConfig(
        output_dir=out,
        num_epochs=int(sys.argv[4]) if len(sys.argv) > 4 else 6,
        batch_size=8,
        encoder_lr=3e-5,
        task_lr=5e-4,
        fp16=False,
        eval_strategy="epoch",
        save_best=True,
        save_total_limit=1,
        early_stopping=True,
        early_stopping_patience=2,
        num_workers=0,
        pin_memory=False,
        logging_steps=20,
    )
    ExtractorTrainer(model, config).train(train_data=train, eval_data=val)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
