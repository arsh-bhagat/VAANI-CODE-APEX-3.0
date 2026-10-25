import tempfile
import unittest
from pathlib import Path

import numpy as np

from prepare_loose_drop import prepare, TASK
from train import load_training_data, split_groups


class LooseDropDataTests(unittest.TestCase):
    def write_trial(self, root, name, label, count=150, participant="p1"):
        path = root / name
        with path.open("w", encoding="utf-8") as out:
            out.write(f"# participant={participant},device=test_phone,label={label}\n")
            out.write("timestamp_ns,ax,ay,az,gx,gy,gz\n")
            for i in range(count):
                out.write(f"{i * 20_000_000},0,0,9.81,0,0,0\n")

    def review(self, kind):
        return dict(reviewed=True, kind=kind,
                    impact_timestamps_ns=[1_600_000_000] if kind == "loose_phone_drop" else [])

    def test_mounted_impacts_cannot_be_relabelled_as_loose_drops(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "mounted/headform"):
                prepare(Path(directory), {"impact.csv": self.review("mounted_headform")})

    def test_short_recording_is_rejected_instead_of_padded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_trial(root, "drop.csv", "DROP", count=25)
            review = self.review("loose_phone_drop")
            review["impact_timestamps_ns"] = [100_000_000]
            with self.assertRaisesRegex(ValueError, "pre / 0.5s post"):
                prepare(root, {"drop.csv": review})

    def test_unreviewed_recording_is_rejected(self):
        review = self.review("loose_phone_drop")
        review["reviewed"] = False
        with self.assertRaisesRegex(ValueError, "not been reviewed"):
            prepare(Path("unused"), {"drop.csv": review})

    def test_label_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_trial(root, "drop.csv", "NORMAL")
            with self.assertRaisesRegex(ValueError, "disagree"):
                prepare(root, {"drop.csv": self.review("loose_phone_drop")})

    def test_both_classes_and_participant_separation_without_snatches(self):
        # These synthetic fixtures only test data plumbing; they never train/export a model.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reviews = {}
            for participant in range(5):
                for kind, label in [("normal_handling", "NORMAL"), ("loose_phone_drop", "DROP")]:
                    name = f"p{participant}_{label}.csv"
                    self.write_trial(root, name, label, participant=f"p{participant}")
                    reviews[name] = self.review(kind)
            result = prepare(root, reviews)
            self.assertEqual(TASK, result["task"].item())
            self.assertEqual(["NORMAL", "DROP"], result["classes"].tolist())
            self.assertEqual((10, 100, 6), result["X"].shape)
            path = root / "dataset.npz"
            np.savez_compressed(path, **result)
            x, y, groups = load_training_data(path, loose_drop=True)
            self.assertEqual(10, len(x))
            splits = split_groups(groups)
            for indices in splits:
                self.assertEqual({0, 1}, set(y[indices]))
            self.assertFalse(set(groups[splits[0]]) & set(groups[splits[2]]))

    def test_legacy_or_unprovenanced_npz_cannot_enter_binary_training(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "data.npz"
            np.savez_compressed(path, X=np.zeros((2, 100, 6)), y=[0, 1], groups=["a", "b"])
            with self.assertRaisesRegex(ValueError, "reviewed actual loose-phone"):
                load_training_data(path, loose_drop=True)


if __name__ == "__main__":
    unittest.main()
