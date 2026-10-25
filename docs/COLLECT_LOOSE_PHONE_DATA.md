# Collect, review and train loose-phone motion data

These commands never run Gradle, build an APK, install an app, send SMS or start emergency recording. The phone tools use the isolated debug recorder in the existing installed VAANI debug app. `Setup` installs Python training packages only when you explicitly run it. `Train` starts model training only when you explicitly run it.

## 1. Define the command in PowerShell

```powershell
Set-Location D:\CodeApex
function motion { powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\CodeApex\scripts\motion\motion.ps1 @args }
```

The function lasts only for this PowerShell session. The execution-policy option applies only to the child PowerShell process; no permanent policy is changed. All data paths default to this repository, even when commands run from another directory.

## 2. Connect and open the recorder

Enable USB debugging on the phone, connect USB, unlock it and accept the debugging prompt. VAANI must be installed as a debuggable APK containing `MotionRecorderActivity`.

```powershell
motion -Action Check
motion -Action Open
```

If multiple Android devices/emulators are connected, pass the same device serial for phone commands:

```powershell
motion -Action Check -Serial YOUR_DEVICE_SERIAL
motion -Action Open -Serial YOUR_DEVICE_SERIAL
motion -Action Pull -Serial YOUR_DEVICE_SERIAL
```

The default ADB executable is `D:\CodeApex\.android-sdk\platform-tools\adb.exe`; override with `-AdbPath` if necessary. The scripts never install or replace an APK. If the installed version is not debuggable or does not contain the recorder, it must be replaced with an appropriate existing debug APK before collection; these scripts will explain the failure.

## 3. Record on the phone

- Enter a real anonymous participant ID, such as `p01`. Reuse that person's ID for all of their recordings; do not invent extra participants to satisfy a split.
- NORMAL: record walking, stairs, setting down/picking up, shaking, pocket and bag handling without drops or snatches.
- DROP: use a spare protected phone and padded landing area. Start recording, wait at least **two seconds**, release the phone completely, wait at least **one second after landing**, then tap **Stop and save**. Keep the app in the foreground. Record one drop per trial to simplify review. Do not attach the phone to a body or fixture.
- Wait for the **Saved** message before exporting. Recordings stop if you leave the recorder or after five minutes. Reopen it with `motion -Action Open` if needed.

For an initial pilot, collect 10 separate drops plus five minutes of normal handling per person from five actual people, with additional devices when available. This is a collection target, not a reliability guarantee. Each person should have both NORMAL and DROP trials. More varied data and independent physical-device validation will still be needed.

## 4. Export completed recordings

```powershell
motion -Action Pull
```

Files go to `D:\CodeApex\motion-dataset\verified-trials`. Despite the directory's name, new files are **not approved for training** until reviewed. CSV contents are copied as bytes without PowerShell text re-encoding. Repeating Pull checks/skips identical files, refuses to overwrite different contents, writes a checksum manifest and leaves originals on the phone. Multiple phones can export into the same directory because recordings use session UUIDs. File metadata preserves participant and phone model; export manifests also record the originating device serial. This folder is ignored by Git.

## 5. Review plots and mark impacts

```powershell
motion -Action Review
```

This starts a browser review page at `http://127.0.0.1:8765/`, accessible only on this computer. It uses Python's standard library and requires no TensorFlow installation. If the page does not open, visit that URL manually, or use `-NoBrowser`. If the port is occupied, use `-Port 8766`.

For each DROP trial, click the graph at the landing spike. **Suggest strongest impact** is only a hint: check it is the actual landing rather than pickup. Tick the checkbox confirming a freely released phone and correct impact marks, then save. For NORMAL trials, inspect the trace, confirm there were no drops or snatches and save. Leave uncertain trials unreviewed. SNATCH files are excluded from this binary workflow.

The page writes `reviews.json` using the actual sample timestamps and binds each review to the CSV's SHA-256. It rejects short/gapped event windows, missing confirmation and changed source files. It does not alter CSVs, relabel headform data or approve suggestions automatically. Use **Ctrl+C** in PowerShell to stop the review server before continuing.

## 6. Set up the training environment once

TensorFlow 2.13 in this project requires **Python 3.10 or 3.11**. Python 3.12 can run Check/Open/Pull/Review, but not this pinned training environment. Install Python 3.11 separately if it is missing; Setup does not install Python itself.

```powershell
motion -Action Setup
```

Setup first tries the Windows Python launcher's `py -3.11`, then the default Python executable. To select a specific installation:

```powershell
motion -Action Setup -PythonPath "$env:LOCALAPPDATA\Programs\Python\Python311\python.exe"
```

The interpreter path above is the usual per-user installation path; use the actual path if different. Setup creates `.venv-motion` and installs the pinned requirements there; it never replaces a mismatched existing environment automatically. No app build or training starts at this step.

## 7. Prepare reviewed data and train

```powershell
motion -Action Prepare
motion -Action Train
```

Prepare writes `motion-dataset\loose-drop.npz` from approved NORMAL/DROP trials only. Positive windows use real, uninterrupted two-second recordings; nothing is padded. Train requires at least five participant groups and both classes in every participant split. It trains the binary model, evaluates the converted TFLite on held-out participants and writes:

```text
D:\CodeApex\motion-output\loose-drop\loose_phone_drop_classifier.tflite
D:\CodeApex\motion-output\loose-drop\loose_phone_drop_classifier.json
D:\CodeApex\motion-output\loose-drop\evaluation.json
```

Use `motion -Action Train -Epochs 60` to select a different epoch limit. Training can take time; a GPU is not required. Neither command copies the model into app assets or builds/installs an APK. Review evaluation and device replay before integrating the exported files. Participant-held-out window scores do not establish accuracy on unseen phones or false alarms during continuous monitoring.

## Script checks

```powershell
python -m unittest discover -s D:\CodeApex\scripts\motion -p "test_*.py" -v
```

The tests use synthetic fixtures solely to check file handling and pipeline correctness; they do not train or create a model. No downloaded headform impacts are used as loose-phone positive examples. See `LOOSE_PHONE_DROP_DATA_AUDIT.md` for the source audit.
