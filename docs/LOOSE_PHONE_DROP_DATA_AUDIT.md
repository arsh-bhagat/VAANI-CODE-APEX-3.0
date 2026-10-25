# Loose-phone drop data and ML integration

For ready-to-run USB capture, export, browser review and training commands, see [COLLECT_LOOSE_PHONE_DATA.md](COLLECT_LOOSE_PHONE_DATA.md).

The downloaded research data does **not** establish loose-phone fall detection. The source's `pipelines/raw_impact_filtering_phone_translation_CB_V2.py` states that the phones were physically present and mounted on a headform during all impact tests. `pipelines/transforms/framing.py:trim_reference` crops signals to approximately -0.1 to +0.4 seconds around impact; some retained files are shorter. These mounted impacts must not be called independently released phone drops, padded into full trials, or counted as validation of a loose-phone classifier.

Source: https://github.com/maximweill/PhoneRoadCrashReseach/tree/3c38f017e711866c6e44329e993115c596ad9aca

The local download contains 200 phone signal files, not 200 independent loose drops. Multiple phones recorded the same experiments. All 200 files opened successfully with finite six-axis values and increasing timestamps. Durations are approximately 0.248–0.499 seconds. The repository declares no reuse license. UCI HAR provides waist-mounted normal activities, not phone-drop positives; its website's CC BY 4.0 listing also differs from the legacy archive README's commercial-use prohibition. Original data and documentation remain unchanged.

Reproduce the inspection without building the app:

```powershell
python scripts/motion/audit_public_data.py
```

The audit writes `motion-dataset/internet/public-data-audit.json` and explicitly reports zero eligible loose-phone positive recordings. Pandas, NumPy and PyArrow are required for this inspection. This audit does not train a model or change source recordings.

## Actual loose-phone recording requirements

Use the existing isolated debug `MotionRecorderActivity` with a test phone and padded landing area. Select DROP only when releasing the phone completely; do not attach it to a person or fixture. Keep the recording running for at least two seconds before and one second after each event, and then review the actual impact timestamp. Include varied release orientations, spinning and non-spinning drops, soft landings, devices and participants. NORMAL trials should include setting down, shaking, walking, pockets and bags. Do not label ordinary sections of a DROP trial as drops.

CSV source recordings contain raw acceleration including gravity in m/s², angular velocity in rad/s, monotonic timestamps at 50 Hz, anonymous participant and device metadata. Preserve complete recordings and actual timestamps; do not manufacture missing history.

Create a separate review manifest after examining each recording:

```json
{
  "normal-session.csv": {
    "reviewed": true,
    "kind": "normal_handling"
  },
  "drop-session.csv": {
    "reviewed": true,
    "kind": "loose_phone_drop",
    "impact_timestamps_ns": [123456789000]
  }
}
```

The timestamp above is illustrative; use actual captured timestamps. Labels in the review must agree with the CSV metadata. Each positive extracts 100 real samples with the impact at index 74 and 25 following samples. Missing history, gaps, unknown placement, mounted-impact reviews and unreviewed trials are rejected rather than padded. Both NORMAL and DROP are required. At least five independent participant groups, each with both classes, are needed by the existing participant split implementation. Repeated windows from a participant cannot leak into other splits. Participant separation is not unseen-device validation; evaluate separate phone models and continuous recordings as additional validation.

```powershell
python scripts/motion/prepare_loose_drop.py motion-dataset/verified-trials --reviews motion-dataset/verified-trials/reviews.json --output motion-dataset/loose-drop.npz
python scripts/motion/train.py motion-dataset/loose-drop.npz --loose-drop --output motion-output/loose-drop
python -m unittest discover -s scripts/motion -p "test_*.py" -v
```

Training uses the existing pinned TensorFlow 2.13 environment and exports a binary NORMAL / DROP CNN. There is no need to collect SNATCH examples for this task. Rotation/noise augmentation applies only after participant splits. Evaluation uses the converted TFLite model on held-out participants. The model's 0.80 decision threshold is provisional; window metrics do not establish continuous false alarms per hour, freefall-gate recall, soft-drop recall or accuracy on unseen phones.

## App behavior

`MotionThreatDetector` now supports `loose_phone_drop_classifier.tflite` plus `loose_phone_drop_classifier.json` in app assets. The manifest checks the task, classes, dimensions, rate, units, threshold and SHA-256 before the interpreter loads. Both files are produced by the binary training command; exporting them does not copy them into the app automatically.

With a valid model installed, physics candidates go through the existing synchronized two-second window, inference runs on a worker, and DROP >= 0.80 opens the existing 15-second safeword confirmation. Snatches retain their separate rule trigger. A model prediction never directly sends SMS or starts emergency recording. Stopping, suppression, sensor gaps and cooldown discard partial history and invalidate pending results. Missing/invalid model files or failed inference retain rule detection. Continuous acoustic listening and motion arming remain independent.

This candidate-gated model is not a universal freefall detector: it still inherits the gate's freefall/impact thresholds and window warmup, so missed candidates cannot be recovered by the model. Foreground-only monitoring also remains unchanged.

No trained model is bundled because no verified loose-phone positive recordings are available locally. The mounted dataset was not used to train or fake one. No app build was run for this change. Actual training, physical-device validation, and Android compilation remain outstanding.
