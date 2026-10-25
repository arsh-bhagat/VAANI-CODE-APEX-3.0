"""Open the isolated Android recorder and export CSV bytes without shell redirection."""
import argparse
import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

PACKAGE = "com.bithead.shelter"
ACTIVITY = f"{PACKAGE}/.debug.MotionRecorderActivity"
SAFE_CSV = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*\.csv\Z")


class Device:
    def __init__(self, adb, serial=None):
        self.adb = str(adb)
        devices = self.run("devices").decode("utf-8", errors="replace")
        states = {}
        for line in devices.splitlines():
            fields = line.split()
            if len(fields) >= 2 and not line.startswith("List of devices"):
                states[fields[0]] = fields[1]
        ready = [name for name, state in states.items() if state == "device"]
        if serial:
            if states.get(serial) != "device":
                raise ValueError(f"Device {serial!r} is not authorized/online: {states.get(serial, 'not found')}")
            self.serial = serial
        elif len(ready) == 1:
            self.serial = ready[0]
        else:
            raise ValueError(f"Connect and authorize one USB-debugging device, or use -Serial. Found: {states}")
        package = self.command("shell", "pm", "path", PACKAGE).decode("utf-8", errors="replace")
        if not package.strip().startswith("package:"):
            raise ValueError("VAANI is not installed on this device. Install an existing debug APK first; this tool never builds or installs it.")
        self.command("exec-out", "run-as", PACKAGE, "id")

    def run(self, *args):
        result = subprocess.run([self.adb, *args], capture_output=True, timeout=45, check=False)
        if result.returncode:
            message = (result.stderr + result.stdout).decode("utf-8", errors="replace").strip()
            raise ValueError(f"adb failed: {message}\nIf run-as is denied, install the debug version of VAANI.")
        return result.stdout

    def command(self, *args):
        return self.run("-s", self.serial, *args)

    def open(self):
        output = self.command("shell", "am", "start", "-n", ACTIVITY).decode("utf-8", errors="replace")
        if re.search(r"Error:|Exception|does not exist", output, re.IGNORECASE):
            raise ValueError(f"Recorder could not be opened: {output.strip()}")
        print(output.strip())

    def pull(self, directory):
        directory.mkdir(parents=True, exist_ok=True)
        try:
            listing = self.command("exec-out", "run-as", PACKAGE, "ls", "files/motion-dataset")
        except ValueError as error:
            raise ValueError("No recording directory is available yet. Save a recording in the debug recorder first.") from error
        names = [name.strip() for name in listing.decode("utf-8").splitlines() if name.strip()]
        csv_names = [name for name in names if SAFE_CSV.fullmatch(name)]
        records = []
        for name in sorted(csv_names):
            remote = f"files/motion-dataset/{name}"
            before = int(self.command("exec-out", "run-as", PACKAGE, "stat", "-c", "%s", remote).strip())
            content = self.command("exec-out", "run-as", PACKAGE, "cat", remote)
            after = int(self.command("exec-out", "run-as", PACKAGE, "stat", "-c", "%s", remote).strip())
            if len(content) != before or before != after or not content.endswith(b"\n"):
                raise ValueError(f"{name} changed during export. Wait for the phone's Saved message and retry.")
            if not content.startswith(b"# participant=") or b"timestamp_ns,ax,ay,az,gx,gy,gz" not in content.splitlines()[:2]:
                raise ValueError(f"{name} is not a motion recorder CSV")
            path = directory / name
            status = "unchanged"
            if path.exists():
                if path.read_bytes() != content:
                    raise ValueError(f"Refusing to overwrite a different existing recording: {path}")
            else:
                temporary = path.with_suffix(".csv.partial")
                temporary.write_bytes(content)
                temporary.replace(path)
                status = "downloaded"
            records.append(dict(file=name, bytes=len(content), sha256=hashlib.sha256(content).hexdigest(), status=status))
        manifest = directory / f"export-{hashlib.sha256(self.serial.encode()).hexdigest()[:12]}.json"
        manifest.write_text(json.dumps(dict(device_serial=self.serial, exported_utc=datetime.now(timezone.utc).isoformat(),
                                            package=PACKAGE, recordings=records), indent=2), encoding="utf-8")
        print(f"Exported/verified {len(records)} recordings in {directory}; originals remain on the phone.")
        if not records:
            print("No completed CSV recordings found. Use Start recording, then Stop and save on the phone.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["check", "open", "pull"])
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial")
    parser.add_argument("--output", type=Path, default=Path("motion-dataset/verified-trials"))
    args = parser.parse_args()
    try:
        device = Device(args.adb, args.serial)
        print(f"Connected: {device.serial}; VAANI debug storage is accessible.")
        if args.action == "open":
            device.open()
        elif args.action == "pull":
            device.pull(args.output.resolve())
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
