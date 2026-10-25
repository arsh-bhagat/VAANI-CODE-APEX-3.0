# Motion detection implementation

The app currently uses **rule-based foreground motion detection without a bundled trained model**. It also supports an optional binary NORMAL / loose-phone DROP model when a matching trained asset and manifest are installed. Missing or invalid models retain the rules. See [LOOSE_PHONE_DROP_DATA_AUDIT.md](LOOSE_PHONE_DROP_DATA_AUDIT.md) for the downloaded dataset's mounted-phone limitation and the new binary training workflow. A candidate or accepted model prediction opens safeword confirmation; it never directly activates an emergency. Detection accuracy has not been validated on physical devices.

## Runtime contract

- Raw `TYPE_ACCELEROMETER` including gravity, m/s²; `TYPE_GYROSCOPE`, rad/s.
- Event timestamps are monotonic nanoseconds. Both streams are linearly interpolated at a shared 50 Hz watermark. Source gaps over 60 ms, non-finite values and out-of-order samples reset history. Sensor registration requests 20 ms; it is not assumed to guarantee that cadence.
- Candidate gate: acceleration magnitude below 3 m/s² for at least 60 ms, followed by magnitude above 35 m/s² within 600 ms; or magnitude jerk above 150 m/s³ with angular speed above 6 rad/s. These are provisional thresholds requiring device validation.
- With no loose-drop model installed, `RuleBasedMotionTrigger` routes candidates directly to confirmation, without model warmup or inference delay. With a valid binary model, drop candidates use the model window and snatches retain their rule trigger. A 5-second cooldown prevents duplicate windows. Candidates are suppressed during confirmation, emergency capture and sealing; partial gesture history is discarded on suppression, sensor gaps and pause. Stopping/restarting preserves cooldown within the detector instance.
- Enable **Settings → Motion confirmation (15s window)**, allow microphone access, and keep VAANI or Notes open in the foreground. Both accelerometer and gyroscope are required. Ordinary gentle rotation alone is intentionally insufficient to trigger a window. When the continuous acoustic trigger is off, the microphone becomes active only when the rules detect a candidate. Missing sensors prevent enabling the mode with an explanatory message.
- Motion arming and the **Continuous acoustic trigger** are independent preferences. With the acoustic trigger off, motion still opens a 15-second listening window and a recognized safeword can activate the emergency. Outside that window the microphone stays off. If both toggles are on, continuous acoustic listening runs too; after the motion window expires, continuous listening resumes. Disabling the acoustic toggle during a window does not cancel motion confirmation. Disabling motion cancels its active window, while leaving the acoustic preference unchanged.

### Legacy three-class ML contract (inactive)

- Capture 100 synchronized samples covering `(trigger - 1.5s, trigger + 0.5s]`. The trigger is index 74; the final frame is index 99. Warmup requires 75 frames. Gaps discard pending candidates.
- Normalize acceleration by 9.81 and gyro by pi in both training and Android. Channel order: `ax,ay,az,gx,gy,gz`.
- Model asset: `app/src/main/assets/motion_threat_classifier.tflite`. FLOAT32 input `[1,100,6]`; FLOAT32 output `[1,3]`, class order NORMAL / DROP / SNATCH. Internal weights/activations may be INT8. Output probabilities must be finite, in [0,1], and sum approximately to 1.
- Provisional legacy model decision: DROP >= .80 or SNATCH >= .75. The legacy three-class asset is not loaded by the active detector. Only the separate binary loose-drop asset plus matching manifest enables the new ML path.

## Confirmation and emergency behavior

The microphone starts after a physics candidate while motion mode is armed. The 15-second clock starts at recognizer readiness, with a separate 3-second startup timeout. A matching phrase must arrive before the deadline; late final results are rejected. Timeout cancels recognition without recording evidence or sending SMS. Pause, walkthrough suppression, and disabling protection cancel the session. Haptic feedback is emitted when the microphone becomes ready.

Confirmed safewords use the existing emergency path: start recording, send configured trusted-contact SMS, and seal evidence when capture stops. This implementation does not add new vault cryptography, a master PIN, or pre-event audio. It does not guarantee zero false alerts. If the user cannot speak or the phone is out of hearing range, this confirmation policy will not activate an emergency.

Screen-off/background monitoring and microphone foreground service support are not implemented. Existing Android speech-service availability/offline limitations remain. No custom keyword model or guaranteed AEC/noise suppression is added.

## Collect trials (debug builds only)

Launch the isolated recorder using the Android SDK adb:

```powershell
.android-sdk/platform-tools/adb shell am start -n com.bithead.shelter/.debug.MotionRecorderActivity
```

Enter an anonymous participant ID, select NORMAL/DROP/SNATCH, and explicitly start/stop. There is no microphone access, inference, SMS, or evidence capture on this screen. The recorder stops on pause or after 5 minutes and saves CSVs to private `files/motion-dataset/`. It is absent from release manifests. Capture at least 1.5 seconds before and .5 seconds after each event. Use controlled trials with test hardware; do not perform dangerous snatches or damage a personal device.

List files and export a selected recording using adb `run-as` (debug APK must be installed):

```powershell
.android-sdk/platform-tools/adb shell run-as com.bithead.shelter ls files/motion-dataset
.android-sdk/platform-tools/adb shell run-as com.bithead.shelter cat files/motion-dataset/SESSION.csv > SESSION.csv
```

CSV metadata contains participant, session, class, device and gap count. Each row contains the resampled timestamp and six raw channels. Keep private datasets out of version control. Distinguish walking, jogging, desks, bags, pockets, cushions, stairs and vehicle vibration in trial notes; include varied devices and participants.

## Curate, train and export

Use a separate **Python 3.10/3.11** environment and `pip install -r scripts/motion/requirements.txt`. TensorFlow is pinned to 2.13 to match the Android runtime. It is not installed by this change.

Review each positive trial to identify impact/snatch timestamps in its CSV. Create annotations such as `{"SESSION.csv": [123456789000]}`. Only annotated positive windows are labeled DROP/SNATCH; ordinary portions of a positive trial are not automatically labeled as threats. Normal trials produce non-overlapping windows. Windows crossing gaps are excluded; invalid positive annotations raise errors.

```powershell
python scripts/motion/prepare.py DATA_DIRECTORY --annotations annotations.json --output motion-dataset.npz
python scripts/motion/train.py motion-dataset.npz --output motion-output
python -m unittest discover -s scripts/motion -p test_pipeline.py -v
```

The NPZ contains raw `X[N,100,6]`, integer `y[N]`, and participant `groups[N]`. Participants are split before augmentation, with disjoint train/validation/test groups. At least five groups are required; each split must contain all classes. Rotation/noise augmentation is training-only. Normalization matches Android. The CNN follows the proposal; early stopping restores the best validation weights. Quantization calibration uses training data only. Export uses a concrete batch-1 inference function, not the incompatible Keras 3 `model.save(directory)` pattern.

Legacy outputs: `motion.keras`, `motion_threat_classifier.tflite`, `evaluation.json`. Evaluation runs the actual converted model on held-out windows and reports confusion matrix, per-class recall and thresholded threat recall/normal-window false positive rate. Export does not automatically install a model into the app. For the active optional binary ML path, use the separate reviewed loose-drop preparation workflow and `train.py --loose-drop` described in the audit document; it exports `loose_phone_drop_classifier.tflite` and its manifest. There are currently no qualifying loose-drop recordings or trained motion assets locally.

## Verification and remaining validation

Run `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`. Synthetic sensor replay tests cover freefall duration, impact deadlines, rotational gating, asynchronous alignment, exact window boundaries, gap resets, normalization and invalid output rejection. Python tests cover event alignment, gap exclusion, participant separation and augmentation consistency.

Rule-based tests additionally cover immediate triggering without a model or window warmup, cooldown across reset, suppression during confirmation/emergency, discarded partial gestures and normal handling. These tests check software behavior, not real-world reliability. Real drops/snatches can be missed; ordinary handling can open listening windows. Speech recognition can miss or falsely match a phrase.

Before deployment: evaluate gate recall on complete annotated recordings, not just curated windows; then end-to-end candidate/confirmation recall, false candidates per monitoring hour, false confirmed alerts, and battery/latency on physical devices. Include saturating sensors, unavailable gyros, permission denial, delayed recognizer readiness, pause/recreation, and microphone handoff to evidence recording. Thresholds and confidence scores have not been calibrated. Training/export and physical-device behavior cannot be validated without real data and hardware.
