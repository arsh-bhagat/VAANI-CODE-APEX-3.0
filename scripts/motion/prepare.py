"""Curate CSV windows using reviewed impact/snatch timestamps.

Annotations JSON maps CSV filenames to event timestamps in nanoseconds:
{"session.csv": [123456789000]}. Normal trials are windowed without annotations.
"""
import argparse
import json
from pathlib import Path

import numpy as np

CLASSES = {"NORMAL": 0, "DROP": 1, "SNATCH": 2}
PERIOD_NS = 20_000_000


def read_trial(path):
    with path.open(encoding="utf-8") as source:
        metadata = dict(field.split("=", 1) for field in source.readline().strip().removeprefix("# ").split(",") if "=" in field)
    timestamps = np.loadtxt(path, delimiter=",", skiprows=2, usecols=0, dtype=np.int64, ndmin=1)
    values = np.loadtxt(path, delimiter=",", skiprows=2, usecols=range(1, 7), dtype=np.float32, ndmin=2)
    if not len(timestamps) or not np.isfinite(values).all() or not np.all(np.diff(timestamps) > 0):
        raise ValueError(f"{path}: empty, invalid or unordered trial")
    if metadata.get("label") not in CLASSES or not metadata.get("participant"):
        raise ValueError(f"{path}: missing participant/label")
    return metadata, timestamps, values


def windows(timestamps, values, label, events):
    if label == 0:
        starts = range(0, len(values) - 99, 100)
    else:
        if not events:
            raise ValueError("Threat trials require reviewed event timestamp annotations")
        starts = []
        for event in events:
            center = int(np.argmin(np.abs(timestamps - int(event))))
            if abs(int(timestamps[center]) - int(event)) > PERIOD_NS:
                raise ValueError(f"Event {event} does not align with a captured frame")
            starts.append(center - 74)
    for start in starts:
        end = start + 100
        if start < 0 or end > len(values) or not np.all(np.diff(timestamps[start:end]) == PERIOD_NS):
            if label != 0:
                raise ValueError("Annotated threat lacks uninterrupted 1.5s pre / 0.5s post history")
            continue
        yield values[start:end].copy()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--annotations", type=Path)
    parser.add_argument("--output", type=Path, default=Path("motion-dataset.npz"))
    args = parser.parse_args()
    annotations = json.loads(args.annotations.read_text(encoding="utf-8")) if args.annotations else {}
    x, y, groups = [], [], []
    for path in sorted(args.directory.glob("*.csv")):
        metadata, timestamps, values = read_trial(path)
        label = CLASSES[metadata["label"]]
        for window in windows(timestamps, values, label, annotations.get(path.name, [])):
            x.append(window); y.append(label); groups.append(metadata["participant"])
    if not x:
        raise ValueError("No complete windows found")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(args.output, X=np.stack(x), y=np.asarray(y, dtype=np.int64), groups=np.asarray(groups))
    print(f"Saved {len(x)} raw windows; class counts={np.bincount(y, minlength=3).tolist()}")


if __name__ == "__main__":
    main()
