"""Prepare verified, complete NORMAL / loose-phone DROP trials without padding.

Review JSON maps CSV names to {reviewed: true, kind: normal_handling |
loose_phone_drop, impact_timestamps_ns: [...]}. Mounted impacts are not drops.
CSV files use the debug recorder's raw 50 Hz, six-channel format.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from prepare import read_trial, windows

TASK = "loose_phone_drop_v1"


def prepare(directory, reviews):
    x, y, groups, devices, trials = [], [], [], [], []
    if not reviews:
        raise ValueError("Reviewed loose-phone drops and normal trials are required")
    for name, review in sorted(reviews.items()):
        if Path(name).name != name or not name.endswith(".csv"):
            raise ValueError("Reviews must reference CSV filenames in the trial directory")
        if review.get("reviewed") is not True:
            raise ValueError(f"{name}: trial has not been reviewed")
        expected_hash = review.get("source_sha256")
        if expected_hash and hashlib.sha256((directory / name).read_bytes()).hexdigest() != expected_hash:
            raise ValueError(f"{name}: source recording changed after review")
        kind = review.get("kind")
        if kind not in {"normal_handling", "loose_phone_drop"}:
            raise ValueError(f"{name}: mounted/headform/unknown impacts cannot be loose-phone DROP examples")
        metadata, timestamps, values = read_trial(directory / name)
        label = 1 if kind == "loose_phone_drop" else 0
        if metadata["label"] != ("DROP" if label else "NORMAL"):
            raise ValueError(f"{name}: review and recording label disagree")
        if not metadata.get("device"):
            raise ValueError(f"{name}: device metadata is required")
        events = review.get("impact_timestamps_ns", [])
        if label == 0 and events:
            raise ValueError(f"{name}: normal trials cannot contain marked drops")
        count = 0
        for window in windows(timestamps, values, label, events):
            x.append(window)
            y.append(label)
            groups.append(metadata["participant"])
            devices.append(metadata["device"])
            trials.append(name)
            count += 1
        if not count:
            raise ValueError(f"{name}: no complete uninterrupted two-second windows")
    if set(y) != {0, 1}:
        raise ValueError("Both normal handling and verified loose-phone drops are required")
    return dict(X=np.stack(x), y=np.asarray(y, dtype=np.int64),
                groups=np.asarray(groups), devices=np.asarray(devices), trials=np.asarray(trials),
                task=np.asarray(TASK), classes=np.asarray(["NORMAL", "DROP"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--reviews", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("motion-dataset/loose-drop.npz"))
    args = parser.parse_args()
    dataset = prepare(args.directory, json.loads(args.reviews.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(args.output, **dataset)
    print(f"Saved {len(dataset['y'])} verified windows; NORMAL/DROP counts={np.bincount(dataset['y']).tolist()}")


if __name__ == "__main__":
    main()
