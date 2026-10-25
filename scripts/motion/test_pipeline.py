import unittest

import numpy as np

from prepare import windows
from train import normalize, split_groups, augment


class DatasetContractTests(unittest.TestCase):
    def test_event_alignment_matches_android(self):
        timestamps = np.arange(200, dtype=np.int64) * 20_000_000
        values = np.arange(1200, dtype=np.float32).reshape(200, 6)
        window = list(windows(timestamps, values, 1, [int(timestamps[100])]))[0]
        np.testing.assert_array_equal(window[74], values[100])
        np.testing.assert_array_equal(window[-1], values[125])

    def test_gap_cannot_be_training_window(self):
        timestamps = np.arange(100, dtype=np.int64) * 20_000_000
        timestamps[50:] += 20_000_000
        values = np.zeros((100, 6), dtype=np.float32)
        self.assertEqual([], list(windows(timestamps, values, 0, [])))
        with self.assertRaises(ValueError):
            list(windows(timestamps, values, 1, [int(timestamps[74])]))

    def test_split_has_no_participant_leakage(self):
        groups = np.repeat(np.arange(10), 20)
        splits = split_groups(groups)
        sets = [set(groups[index]) for index in splits]
        for i in range(3):
            for j in range(i + 1, 3):
                self.assertFalse(sets[i] & sets[j])
        self.assertEqual(200, sum(map(len, splits)))

    def test_normalization_uses_raw_acceleration_and_radians(self):
        result = normalize(np.array([[[0, 0, 9.81, np.pi, 0, 0]]]))
        np.testing.assert_allclose(result, [[[0, 0, 1, 1, 0, 0]]], atol=1e-6)

    def test_rotation_keeps_acceleration_and_gyro_aligned(self):
        raw = np.ones((2, 100, 6), dtype=np.float32)
        rotated = augment(raw, np.random.default_rng(42))
        self.assertLess(float(np.abs(rotated[:, :, :3] - rotated[:, :, 3:]).mean()), .1)


if __name__ == "__main__":
    unittest.main()
