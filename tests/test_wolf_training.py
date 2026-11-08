"""Fast offline contracts for bounded, explicit CPU router training."""
import json
import tempfile
import unittest
from pathlib import Path

from wolf_ai.train_router import (
    LABELS,
    fit,
    load_examples,
    predict,
    split,
    train,
)


class RouterTrainingTests(unittest.TestCase):
    def test_dataset_and_train_test_separation(self):
        rows = load_examples(Path("training/tasks.jsonl"))
        self.assertGreaterEqual(len(rows), 48)
        self.assertEqual(set(x["project"] for x in rows), set(LABELS))
        tr, test = split(rows)
        self.assertFalse({x["text"] for x in tr} & {x["text"] for x in test})
        self.assertEqual(len(tr) + len(test), len(rows))
        self.assertTrue(all(sum(x["project"] == label for x in test) >= 2 for label in LABELS))

    def test_real_training_writes_model_and_report(self):
        with tempfile.TemporaryDirectory() as td:
            report = train(Path("training/tasks.jsonl"), Path(td))
            self.assertEqual(report["status"], "trained")
            self.assertFalse(report["model_weights_changed"])
            self.assertFalse(report["gpu_used"])
            self.assertEqual(report["external_calls"], 0)
            self.assertGreaterEqual(report["holdout"]["macro_recall"], 0.6)
            obj = json.loads((Path(td) / "task_router_model.json").read_text())
            self.assertEqual(obj["type"], "multinomial-naive-bayes")
            self.assertEqual(set(obj["labels"]), set(LABELS))
            self.assertGreater(obj["vocab_size"], 25)

    def test_predict_is_deterministic_and_not_lm(self):
        rows = load_examples(Path("training/tasks.jsonl"))
        model = fit(rows)
        choice, scores = predict(model, "Проверь ошибки WOLF AI")
        self.assertEqual(choice, "wolf")
        self.assertEqual(set(scores), set(LABELS))
        self.assertFalse(model["learned_lm_weights"])

    def test_reject_corrupted_labels_duplicates_and_extra_fields(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "bad.jsonl"
            path.write_text(json.dumps({"text":"hello world", "project":"admin"})+"\n")
            with self.assertRaises(ValueError):
                load_examples(path)
            path.write_text(json.dumps({
                "text":"hello world", "project":"wolf", "secret":"hidden"})+"\n")
            with self.assertRaises(ValueError):
                load_examples(path)
            path.write_text("\n".join([json.dumps({
                "text":"identical content", "project":"wolf"})] * 10))
            with self.assertRaises(ValueError):
                load_examples(path)


if __name__ == "__main__":
    unittest.main()
