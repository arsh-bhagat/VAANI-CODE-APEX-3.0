"""Inspect downloaded recordings without relabelling mounted impacts as loose drops."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import pandas as pd


def audit(root):
    source = root / "phone-drop-research"
    evidence = source / "pipelines/raw_impact_filtering_phone_translation_CB_V2.py"
    text = evidence.read_text(encoding="utf-8")
    mounted = "Phones were" in text and "physically present and mounted on the headform during all tests" in text
    records = []
    channels = [f"LinAcc{axis} (m/s2)" for axis in "XYZ"] + [f"RotVel{axis} (rad/s)" for axis in "XYZ"]
    for path in sorted((source / "my_app/data/phone_drop_test_data_parquet/phone_framed").glob("*.parquet")):
        frame = pd.read_parquet(path)
        times = frame["Time (s)"].to_numpy()
        records.append(dict(file=path.name, frames=len(frame),
                            duration_s=float(times[-1] - times[0]),
                            finite=bool(np.isfinite(frame[channels]).all().all()),
                            ordered=bool((np.diff(times) > 0).all())))
    if not records:
        raise ValueError("No downloaded phone recordings found")
    result = dict(
        source_repository="https://github.com/maximweill/PhoneRoadCrashReseach",
        source_evidence=str(evidence.relative_to(root)),
        evidence_sha256=hashlib.sha256(evidence.read_bytes()).hexdigest(),
        experiment="mounted_headform_impacts" if mounted else "unverified",
        downloaded_recordings=len(records), eligible_loose_drop_recordings=0,
        eligible_for_binary_training=False,
        reasons=["Phones were mounted on a headform, not independently released" if mounted else "Loose-phone experimental conditions are unverified",
                 "Impact windows do not provide the required 1.5-second pre-event and 0.5-second post-event history",
                 "Source repository has no declared reuse license"],
        normal_data=dict(source="UCI HAR", placement="waist_mounted", role="supplementary_normal_activity_only",
                         licensing="UCI page says CC BY 4.0; bundled README prohibits commercial use; discrepancy unresolved",
                         limitation="Filtered windowed data, not complete loose-drop trials or desk/bag/hand handling coverage"),
        min_duration_s=min(row["duration_s"] for row in records),
        max_duration_s=max(row["duration_s"] for row in records),
        all_finite=all(row["finite"] for row in records),
        all_timestamps_ordered=all(row["ordered"] for row in records),
        recordings=records,
    )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, nargs="?", default=Path("motion-dataset/internet"))
    args = parser.parse_args()
    result = audit(args.root)
    output = args.root / "public-data-audit.json"
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "recordings"}, indent=2))


if __name__ == "__main__":
    main()
