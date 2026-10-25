import hashlib
import json
import subprocess
import tempfile
import threading
import unittest
from http.server import HTTPServer
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from device import Device
from prepare_loose_drop import prepare
from review_trials import approve_recording, catalog, handler_for, read_recording


def csv_bytes(label="DROP", count=150):
    header = f"# participant=p01,device=test_phone,label={label}\ntimestamp_ns,ax,ay,az,gx,gy,gz\n"
    rows = [f"{i * 20_000_000},0,0,{45 if i == 80 else 9.81},0,0,0\n" for i in range(count)]
    return (header + "".join(rows)).encode("utf-8")


class CollectionToolsTests(unittest.TestCase):
    def test_review_uses_exact_original_timestamp_and_requires_confirmation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "drop.csv"
            path.write_bytes(csv_bytes())
            recording = read_recording(path)
            with self.assertRaisesRegex(ValueError, "Confirm"):
                approve_recording(recording, [80], False, recording["source_sha256"])
            review = approve_recording(recording, [80], True, recording["source_sha256"])
            self.assertEqual([1_600_000_000], review["impact_timestamps_ns"])
            self.assertEqual("loose_phone_drop", review["kind"])

    def test_review_rejects_short_history_or_changed_source(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "drop.csv"
            path.write_bytes(csv_bytes())
            recording = read_recording(path)
            with self.assertRaisesRegex(ValueError, "preceding"):
                approve_recording(recording, [5], True, recording["source_sha256"])
            with self.assertRaisesRegex(ValueError, "changed"):
                approve_recording(recording, [80], True, "old-hash")

    def test_review_to_preparation_round_trip_preserves_data(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reviews = {}
            for label, indices in [("NORMAL", []), ("DROP", [80])]:
                path = root / f"{label}.csv"
                path.write_bytes(csv_bytes(label))
                recording = read_recording(path)
                reviews[path.name] = approve_recording(recording, indices, True, recording["source_sha256"])
            (root / "reviews.json").write_text(json.dumps(reviews), encoding="utf-8")
            files, skipped = catalog(root)
            self.assertEqual(2, len(files))
            self.assertTrue(all(file["reviewed"] for file in files))
            self.assertFalse(skipped)
            result = prepare(root, reviews)
            self.assertEqual({0, 1}, set(result["y"]))
            (root / "DROP.csv").write_bytes(csv_bytes().replace(b",45,", b",46,"))
            with self.assertRaisesRegex(ValueError, "changed after review"):
                prepare(root, reviews)

    def test_local_review_http_flow_checks_token_and_saves_exact_marks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "drop.csv").write_bytes(csv_bytes())
            server = HTTPServer(("127.0.0.1", 0), handler_for(root, "test-token"))
            thread = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": .05}, daemon=True)
            thread.start()
            url = f"http://127.0.0.1:{server.server_port}"
            try:
                with urlopen(url + "/files", timeout=3) as response:
                    self.assertEqual("drop.csv", json.load(response)["files"][0]["name"])
                with urlopen(url + "/data?name=drop.csv", timeout=3) as response:
                    data = json.load(response)
                payload = json.dumps(dict(name="drop.csv", indices=[80], confirmed=True,
                                          source_sha256=data["source_sha256"])).encode()
                with self.assertRaises(HTTPError) as rejected:
                    urlopen(Request(url + "/review", data=payload), timeout=3)
                self.assertEqual(403, rejected.exception.code)
                self.assertFalse((root / "reviews.json").exists())
                headers = {"Content-Type": "application/json", "X-Review-Token": "test-token"}
                with urlopen(Request(url + "/review", data=payload, headers=headers), timeout=3) as response:
                    self.assertEqual(200, response.status)
                reviews = json.loads((root / "reviews.json").read_text())
                self.assertEqual([1_600_000_000], reviews["drop.csv"]["impact_timestamps_ns"])
                self.assertEqual(csv_bytes(), (root / "drop.csv").read_bytes())
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=3)

    @patch("device.subprocess.run")
    def test_device_export_is_byte_exact_repeatable_and_non_destructive(self, run):
        content = csv_bytes()
        commands = []

        def command(args, **kwargs):
            commands.append(args)
            if args[-1] == "devices":
                output = b"List of devices attached\nTEST_SERIAL\tdevice\n"
            elif "pm" in args:
                output = b"package:/data/app/vaani/base.apk\n"
            elif "id" in args:
                output = b"uid=12345\n"
            elif "ls" in args:
                output = b"session.csv\n"
            elif "stat" in args:
                output = str(len(content)).encode()
            elif "cat" in args:
                output = content
            else:
                raise AssertionError(args)
            return subprocess.CompletedProcess(args, 0, output, b"")

        run.side_effect = command
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            device = Device("adb.exe")
            device.pull(root)
            device.pull(root)
            self.assertEqual(content, (root / "session.csv").read_bytes())
            manifest = json.loads(next(root.glob("export-*.json")).read_text())
            self.assertEqual(hashlib.sha256(content).hexdigest(), manifest["recordings"][0]["sha256"])
            (root / "session.csv").write_bytes(b"different local recording")
            with self.assertRaisesRegex(ValueError, "overwrite"):
                device.pull(root)
            self.assertEqual(b"different local recording", (root / "session.csv").read_bytes())
        self.assertFalse(any("rm" in command or "install" in command for command in commands))

    @patch("device.subprocess.run")
    def test_multiple_devices_require_explicit_serial(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, b"List of devices attached\none\tdevice\ntwo\tdevice\n", b"")
        with self.assertRaisesRegex(ValueError, "use -Serial"):
            Device("adb.exe")


if __name__ == "__main__":
    unittest.main()
