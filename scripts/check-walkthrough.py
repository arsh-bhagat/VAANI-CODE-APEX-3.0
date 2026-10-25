"""Check walkthrough anchor wiring without starting an Android build."""
from collections import Counter
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/java/com/bithead/shelter/ui"
catalog = (source / "walkthrough/FeatureHelp.kt").read_text(encoding="utf-8")
steps = re.findall(r'WalkthroughStep\(FeatureHelp\.(\w+), (\d), "([^"]+)"\)', catalog)
assert len(steps) == 11
files = {path: path.read_text(encoding="utf-8") for path in source.rglob("*.kt")}
anchors = Counter(re.findall(r"\.featureTarget\(FeatureHelp\.(\w+)\)", "\n".join(files.values())))
for feature, tab, item in steps:
    assert anchors[feature] == 1, f"Missing or duplicate target: {feature}"
    if feature != "NAVIGATION":
        owner = next(text for text in files.values() if f".featureTarget(FeatureHelp.{feature})" in text)
        assert f'item(key = "{item}")' in owner, f"Missing scroll key for {feature}"
overlay = (source / "walkthrough/WalkthroughOverlay.kt").read_text(encoding="utf-8")
assert "boundsInWindow" not in overlay and not re.search(r"\bDialog\(", overlay)
assert "headingHeight" not in overlay and "maxHighlightHeight" not in overlay
assert "coordinates.positionInRoot()" in overlay
assert "target?.translate(-origin)" in overlay
assert "WindowInsets.safeDrawing" in overlay
print("Walkthrough wiring checks passed: 11 unique targets, scroll keys, shared coordinates and system insets.")
