"""Small transparent CPU classifier training for routing WOLF tasks.

This is a real statistical model (multinomial Naive Bayes), but is NOT an LLM
fine-tune and cannot replace Qwen weights, cloud AI, or human review.
All training examples are explicitly checked into this repository.
No paid endpoints, GPU, credentials or third-party data are used.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import math
import re
from datetime import datetime, timezone
from pathlib import Path

LABELS = ("wolf", "studio", "shaira", "williams", "williams-ai", "english-child")
TOKEN = re.compile(r"[a-zа-яё0-9_]{2,}", re.IGNORECASE)
MAX_EXAMPLES = 5000
MAX_VOCAB = 20000


def tokenize(value: str) -> list[str]:
    terms = TOKEN.findall(value.casefold())
    return terms[:100]


def load_examples(path: Path) -> list[dict]:
    examples = []
    seen = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        if set(row) != {"text", "project"}:
            raise ValueError("training schema must only contain text and project")
        text, label = row["text"], row["project"]
        if (not isinstance(text, str) or not 5 <= len(text) <= 240 or
                label not in LABELS or text.casefold() in seen):
            raise ValueError("invalid or duplicate training example")
        if not tokenize(text):
            raise ValueError("training row has no usable tokens")
        seen.add(text.casefold())
        examples.append({"text": text, "project": label})
        if len(examples) > MAX_EXAMPLES:
            raise ValueError("training size limit exceeded")
    counts = collections.Counter(x["project"] for x in examples)
    if any(counts[label] < 8 for label in LABELS):
        raise ValueError("need at least 8 explicitly labeled examples per class")
    return examples


def split(examples: list[dict]) -> tuple[list[dict], list[dict]]:
    by_label = collections.defaultdict(list)
    for row in examples:
        by_label[row["project"]].append(row)
    train, test = [], []
    for label in LABELS:
        # Reproducible within each class, does not depend on file ordering.
        rows = sorted(by_label[label],
                      key=lambda row: hashlib.sha256(row["text"].encode()).hexdigest())
        test_count = max(2, len(rows) // 5)
        train.extend(rows[test_count:])
        test.extend(rows[:test_count])
    return train, test


def fit(rows: list[dict]) -> dict:
    word_counts = {label: collections.Counter() for label in LABELS}
    docs = collections.Counter()
    vocab = set()
    for row in rows:
        label = row["project"]
        words = tokenize(row["text"])
        word_counts[label].update(words)
        docs[label] += 1
        vocab.update(words)
    if len(vocab) > MAX_VOCAB:
        raise ValueError("vocabulary limit exceeded")
    counts = {label: dict(word_counts[label]) for label in LABELS}
    totals = {label: sum(word_counts[label].values()) for label in LABELS}
    return {"schema": 1, "type": "multinomial-naive-bayes", "labels": list(LABELS),
            "vocab_size": len(vocab), "documents": dict(docs), "totals": totals,
            "counts": counts, "trained_examples": len(rows),
            "learned_lm_weights": False}


def predict(model: dict, value: str) -> tuple[str, dict[str, float]]:
    tokens = tokenize(value)
    d = max(1, model["vocab_size"])
    totals = model["totals"]
    total_docs = sum(model["documents"].values())
    score = {}
    for label in LABELS:
        score[label] = math.log((model["documents"].get(label, 0) + 1) /
                                (total_docs + len(LABELS)))
        denom = totals[label] + d
        counts = model["counts"][label]
        for word in tokens:
            score[label] += math.log((counts.get(word, 0) + 1) / denom)
    choice = max(LABELS, key=lambda label: score[label])
    # This is a log-likelihood score, NOT a calibrated probability.
    return choice, score


def evaluate(model: dict, rows: list[dict]) -> dict:
    counts = {label: {"correct": 0, "total": 0} for label in LABELS}
    confusion = {label: {name: 0 for name in LABELS} for label in LABELS}
    for row in rows:
        actual = row["project"]
        predicted, _ = predict(model, row["text"])
        counts[actual]["total"] += 1
        confusion[actual][predicted] += 1
        if predicted == actual:
            counts[actual]["correct"] += 1
    total = sum(row["total"] for row in counts.values())
    correct = sum(row["correct"] for row in counts.values())
    recalls = [counts[label]["correct"] / counts[label]["total"] for label in LABELS]
    return {"holdout_examples": total, "correct": correct,
            "accuracy": round(correct / total, 4),
            "macro_recall": round(sum(recalls) / len(recalls), 4),
            "per_class": counts, "confusion": confusion}


def train(source: Path, output_dir: Path) -> dict:
    rows = load_examples(source)
    tr, test = split(rows)
    model = fit(tr)
    result = evaluate(model, test)
    # After holdout, build the deployable model on all provided training data.
    final = fit(rows)
    final["holdout"] = result
    final["source_sha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
    final["trained_utc"] = datetime.now(timezone.utc).isoformat()
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "task_router_model.json").write_text(
        json.dumps(final, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    receipt = {"schema": 1, "status": "trained", "classifier": final["type"],
               "training_examples": len(rows), "holdout": result,
               "source_sha256": final["source_sha256"],
               "external_calls": 0, "gpu_used": False, "model_weights_changed": False,
               "note": "This is a small intent router, not a fine-tuned Qwen model."}
    (output_dir / "training_report.json").write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--examples", type=Path, default=Path("training/tasks.jsonl"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = train(args.examples, args.output)
    print(json.dumps({k: report[k] for k in ("status", "training_examples", "holdout")}, ensure_ascii=False))
    # A low score is disclosed explicitly. No fake 'AI mastered everything'.
    return 0 if report["holdout"]["macro_recall"] >= 0.60 else 1


if __name__ == "__main__":
    raise SystemExit(main())
