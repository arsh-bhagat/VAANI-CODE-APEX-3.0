"""Review real recorder trials in a local browser; no packages or cloud required."""
import argparse
import csv
import hashlib
import json
import math
import secrets
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from device import SAFE_CSV

PERIOD_NS = 20_000_000


def read_recording(path):
    content = path.read_bytes()
    lines = content.decode("utf-8").splitlines()
    if len(lines) < 3 or not lines[0].startswith("# ") or lines[1] != "timestamp_ns,ax,ay,az,gx,gy,gz":
        raise ValueError("Not a VAANI recorder CSV")
    metadata = dict(part.split("=", 1) for part in lines[0][2:].split(",") if "=" in part)
    if metadata.get("label") not in {"NORMAL", "DROP"}:
        raise ValueError("Only NORMAL and DROP belong in this binary dataset")
    if not metadata.get("participant") or not metadata.get("device"):
        raise ValueError("Participant/device metadata is missing")
    timestamps, values = [], []
    for row in csv.reader(lines[2:]):
        if len(row) != 7:
            raise ValueError("Expected timestamp and six sensor channels")
        timestamp, channels = int(row[0]), [float(value) for value in row[1:]]
        if not all(math.isfinite(value) for value in channels):
            raise ValueError("Non-finite sensor values")
        if timestamps and timestamp <= timestamps[-1]:
            raise ValueError("Timestamps do not strictly increase")
        timestamps.append(timestamp)
        values.append(channels)
    return dict(metadata=metadata, timestamps=timestamps, values=values,
                source_sha256=hashlib.sha256(content).hexdigest())


def complete_window(timestamps, start):
    end = start + 100
    return start >= 0 and end <= len(timestamps) and all(
        timestamps[i] - timestamps[i - 1] == PERIOD_NS for i in range(start + 1, end))


def approve_recording(recording, indices, confirmed, expected_hash):
    if confirmed is not True:
        raise ValueError("Confirm the actual physical recording conditions first")
    if expected_hash != recording["source_sha256"]:
        raise ValueError("The source recording changed; reload and review it again")
    if not isinstance(indices, list) or any(type(index) is not int for index in indices):
        raise ValueError("Impact marks must reference actual sample indices")
    times = recording["timestamps"]
    is_drop = recording["metadata"]["label"] == "DROP"
    if is_drop:
        if not indices:
            raise ValueError("Mark at least one actual landing impact")
        if any(not complete_window(times, index - 74) for index in indices):
            raise ValueError("Each mark needs 74 preceding and 25 following samples at uninterrupted 50 Hz. Record a longer trial or choose the actual impact.")
    elif indices or not any(complete_window(times, start) for start in range(0, len(times) - 99, 100)):
        raise ValueError("A normal trial must have no impact marks and at least one complete two-second window")
    return dict(reviewed=True, kind="loose_phone_drop" if is_drop else "normal_handling",
                impact_timestamps_ns=[times[index] for index in sorted(set(indices))],
                source_sha256=recording["source_sha256"],
                participant=recording["metadata"]["participant"], device=recording["metadata"]["device"])


def load_reviews(directory):
    path = directory / "reviews.json"
    reviews = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    if not isinstance(reviews, dict):
        raise ValueError("reviews.json must contain a JSON object")
    return reviews


def catalog(directory):
    reviews = load_reviews(directory)
    files, skipped = [], []
    for path in sorted(directory.glob("*.csv")):
        if not SAFE_CSV.fullmatch(path.name):
            continue
        try:
            recording = read_recording(path)
            review = reviews.get(path.name, {})
            files.append(dict(name=path.name, **recording["metadata"],
                              reviewed=review.get("reviewed") is True and review.get("source_sha256") == recording["source_sha256"]))
        except (ValueError, KeyError, UnicodeError) as error:
            skipped.append(dict(name=path.name, reason=str(error)))
    return files, skipped


HTML = r'''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>VAANI motion review</title>
<style>body{font:16px system-ui;margin:28px auto;padding:0 20px;max-width:1050px;background:#101419;color:#e8edf2}button,select{font:inherit;padding:10px;margin:5px 5px 5px 0}canvas{width:100%;background:#17202a;display:block;margin:10px 0;border:1px solid #52606d}label{display:block;margin:16px 0}#message{white-space:pre-wrap;color:#ffd76b}#details,#marks{white-space:pre-wrap}.muted{color:#b6c4d2}a{color:#a9d7ff}</style>
<h1>VAANI motion review</h1><p>Review each recording before it enters the training dataset. All files stay on this computer.</p>
<p><select id="files" aria-label="Recording"></select><button id="previous">Previous</button><button id="next">Next</button><span id="count"></span></p>
<p id="details"></p><p id="instructions"></p>
<canvas id="acceleration" width="1000" height="220" aria-label="Acceleration graph"></canvas>
<canvas id="rotation" width="1000" height="220" aria-label="Angular speed graph"></canvas>
<div id="dropControls"><button id="suggest">Suggest strongest impact</button><button id="clear">Clear marks</button><p id="marks"></p></div>
<label><input type="checkbox" id="confirmed"> <span id="confirmation"></span></label>
<button id="save">Save reviewed trial and continue</button><p id="message" role="status"></p>
<p class="muted">For DROP trials, the phone must have been released freely, not attached to a body or fixture. Mark the landing spike, not the later pickup. Each mark needs 1.5 seconds of preceding history and 0.5 seconds after it. Suggestions are only hints; check them before saving. Press Ctrl+C in PowerShell when finished.</p>
<script>
const token=__TOKEN__; let files=[],current=null,marks=[];
const el=id=>document.getElementById(id);
async function api(path,options={}) {const r=await fetch(path,options);const body=await r.json();if(!r.ok)throw Error(body.error||'Request failed');return body;}
async function refresh(selected=null) {const data=await api('/files');files=data.files;el('files').replaceChildren();for(const f of files){let o=document.createElement('option');o.value=f.name;o.textContent=`${f.reviewed?'Reviewed':'Pending'} | ${f.participant} | ${f.label} | ${f.name}`;el('files').append(o);}if(selected&&files.some(f=>f.name===selected))el('files').value=selected;el('count').textContent=`${files.filter(f=>f.reviewed).length}/${files.length} reviewed`;if(!files.length){el('message').textContent='No NORMAL/DROP recordings found. Export saved recordings first.';}if(data.skipped.length){el('message').textContent+='\nSkipped: '+data.skipped.map(f=>f.name+': '+f.reason).join('\n');}}
function draw(id,values,title,color){const c=el(id),ctx=c.getContext('2d'),w=c.width,h=c.height;ctx.clearRect(0,0,w,h);ctx.font='14px system-ui';ctx.fillStyle='#e8edf2';ctx.fillText(title,55,22);let max=1;for(const v of values)max=Math.max(max,v);const duration=current.seconds.at(-1)||1;ctx.strokeStyle='#52606d';ctx.beginPath();ctx.moveTo(50,30);ctx.lineTo(50,h-30);ctx.lineTo(w-20,h-30);ctx.stroke();ctx.fillText(max.toFixed(1),4,40);ctx.fillText('0',20,h-30);ctx.fillText('0 s',50,h-8);ctx.fillText(duration.toFixed(2)+' s',w-100,h-8);ctx.strokeStyle=color;ctx.beginPath();values.forEach((v,i)=>{const x=50+current.seconds[i]/duration*(w-70),y=h-30-v/max*(h-65);if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);});ctx.stroke();ctx.strokeStyle='#ffd76b';for(const i of marks){const x=50+current.seconds[i]/duration*(w-70);ctx.beginPath();ctx.moveTo(x,30);ctx.lineTo(x,h-30);ctx.stroke();}}
function redraw(){if(!current)return;draw('acceleration',current.acceleration,'Acceleration magnitude (m/s²)','#6bd3ff');draw('rotation',current.rotation,'Angular speed (rad/s)','#bda2ff');el('marks').textContent=marks.length?'Marked impacts: '+marks.map(i=>current.seconds[i].toFixed(3)+' s').join(', '):'No impact marked. Click a graph at the landing spike.';}
async function load(){if(!el('files').value)return;current=await api('/data?name='+encodeURIComponent(el('files').value));marks=[...current.indices];el('confirmed').checked=false;const m=current.metadata,drop=m.label==='DROP';el('details').textContent=`${m.participant} | ${m.device} | ${m.label} | ${current.seconds.at(-1).toFixed(2)} s | ${current.seconds.length} samples | ${current.gaps} sensor gaps`;el('dropControls').hidden=!drop;el('instructions').textContent=drop?'Click the acceleration or rotation graph to mark each actual landing impact.':'Inspect this normal-handling trial and confirm it contains no drops or snatches.';el('confirmation').textContent=drop?'I confirm this was a freely released phone drop and the marks identify the actual landing impacts.':'I confirm this recording contains normal handling, with no drops or snatches.';redraw();}
function move(step){if(!files.length)return;const i=el('files').selectedIndex;el('files').selectedIndex=(i+step+files.length)%files.length;load().catch(showError);}
function showError(error){el('message').textContent=error.message;}
for(const id of ['acceleration','rotation'])el(id).onclick=e=>{if(!current||current.metadata.label!=='DROP')return;const c=el(id),x=(e.clientX-c.getBoundingClientRect().left)*c.width/c.getBoundingClientRect().width;if(x<50||x>c.width-20)return;const target=(x-50)/(c.width-70)*current.seconds.at(-1);let best=0;for(let i=1;i<current.seconds.length;i++)if(Math.abs(current.seconds[i]-target)<Math.abs(current.seconds[best]-target))best=i;if(!marks.includes(best))marks.push(best);marks.sort((a,b)=>a-b);el('confirmed').checked=false;redraw();};
el('suggest').onclick=()=>{if(!current)return;let best=-1;for(let i=74;i+25<current.seconds.length;i++)if(best<0||current.acceleration[i]>current.acceleration[best])best=i;if(best<0){showError(Error('This recording is too short. Record at least two seconds before release and one second after landing.'));return;}marks=[best];el('confirmed').checked=false;el('message').textContent='Suggested peak only. Check that it is the landing, not pickup or handling.';redraw();};
el('clear').onclick=()=>{marks=[];el('confirmed').checked=false;redraw();};el('files').onchange=()=>load().catch(showError);el('next').onclick=()=>move(1);el('previous').onclick=()=>move(-1);
el('save').onclick=async()=>{try{if(!current)return;const name=el('files').value,index=el('files').selectedIndex;const result=await api('/review',{method:'POST',headers:{'Content-Type':'application/json','X-Review-Token':token},body:JSON.stringify({name,indices:current.metadata.label==='DROP'?marks:[],confirmed:el('confirmed').checked,source_sha256:current.source_sha256})});el('message').textContent=result.message;await refresh(name);el('files').selectedIndex=(index+1)%files.length;await load();}catch(error){showError(error);}};
refresh().then(load).catch(showError);
</script></html>'''


def handler_for(directory, token):
    class Handler(BaseHTTPRequestHandler):
        def send(self, body, status=200, content_type="application/json"):
            if not isinstance(body, bytes):
                body = json.dumps(body).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Frame-Options", "DENY")
            self.end_headers()
            self.wfile.write(body)

        def recording(self, name):
            if not isinstance(name, str) or not SAFE_CSV.fullmatch(name):
                raise ValueError("Invalid recorder filename")
            return read_recording(directory / name)

        def do_GET(self):
            try:
                parsed = urlparse(self.path)
                if parsed.path == "/":
                    self.send(HTML.replace("__TOKEN__", json.dumps(token)).encode("utf-8"), content_type="text/html; charset=utf-8")
                elif parsed.path == "/files":
                    files, skipped = catalog(directory)
                    self.send(dict(files=files, skipped=skipped))
                elif parsed.path == "/data":
                    name = parse_qs(parsed.query).get("name", [""])[0]
                    recording = self.recording(name)
                    times, values = recording["timestamps"], recording["values"]
                    review = load_reviews(directory).get(name, {})
                    lookup = {timestamp: index for index, timestamp in enumerate(times)}
                    self.send(dict(metadata=recording["metadata"], source_sha256=recording["source_sha256"],
                                   seconds=[(timestamp - times[0]) / 1e9 for timestamp in times],
                                   acceleration=[math.sqrt(sum(value * value for value in row[:3])) for row in values],
                                   rotation=[math.sqrt(sum(value * value for value in row[3:])) for row in values],
                                   gaps=sum(b - a != PERIOD_NS for a, b in zip(times, times[1:])),
                                   indices=[lookup[timestamp] for timestamp in review.get("impact_timestamps_ns", []) if timestamp in lookup]))
                else:
                    self.send(dict(error="Not found"), 404)
            except (ValueError, KeyError, IndexError, OSError) as error:
                self.send(dict(error=str(error)), 400)

        def do_POST(self):
            if self.path != "/review" or self.headers.get("X-Review-Token") != token:
                self.send(dict(error="Invalid review request"), 403)
                return
            try:
                length = int(self.headers.get("Content-Length", 0))
                if not 0 < length <= 65536:
                    raise ValueError("Invalid request size")
                request = json.loads(self.rfile.read(length))
                name = request["name"]
                review = approve_recording(self.recording(name), request["indices"], request.get("confirmed"), request.get("source_sha256"))
                reviews = load_reviews(directory)
                reviews[name] = review
                temporary = directory / "reviews.json.partial"
                temporary.write_text(json.dumps(reviews, indent=2), encoding="utf-8")
                temporary.replace(directory / "reviews.json")
                self.send(dict(message=f"Saved review for {name}. Original CSV unchanged."))
            except (ValueError, KeyError, TypeError, OSError) as error:
                self.send(dict(error=str(error)), 400)

        def log_message(self, format, *args):
            pass
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()
    directory = args.directory.resolve()
    if not directory.is_dir():
        parser.exit(1, "Trial directory is missing. Run Pull after recording first.\n")
    files, skipped = catalog(directory)
    if not files:
        parser.exit(1, f"No valid NORMAL/DROP recordings found. Skipped: {skipped}\n")
    try:
        server = HTTPServer(("127.0.0.1", args.port), handler_for(directory, secrets.token_urlsafe(32)))
    except OSError as error:
        parser.exit(1, f"Could not open local review port: {error}. Choose another -Port.\n")
    url = f"http://127.0.0.1:{server.server_port}/"
    print(f"Review {len(files)} trials at {url}\nReviews: {directory / 'reviews.json'}\nPress Ctrl+C when finished.", flush=True)
    if not args.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Review server stopped.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
