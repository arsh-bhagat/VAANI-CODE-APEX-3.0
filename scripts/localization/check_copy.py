"""Check bundled UI copy and locale-resource parity without building Android."""
import re
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from generate_copy import ROOT, OUTPUT, entries, render

slots = re.compile(r"%([1-9]\d*)\$([sd])")
rows = entries()
seen = {}
for english, hindi, marathi in rows:
    key = english.lower()
    if key in seen:
        assert seen[key] == (hindi, marathi), f"Conflicting translation: {english}"
    seen[key] = (hindi, marathi)
    for translated in (hindi, marathi):
        assert Counter(slots.findall(english)) == Counter(slots.findall(translated)), english
        assert re.search(r"[\u0900-\u097f]", translated) or english in ("SOS",), english
assert OUTPUT.read_text(encoding="utf-8") == render(), "Regenerate UiCopy.kt"

base = ROOT / "app/src/main/res"
resource_count = 0
for name in ("walkthrough", "mesh"):
    original = {s.attrib["name"]: s.text or "" for s in ET.parse(base / f"values/{name}.xml").getroot()}
    resource_count += len(original)
    for code in ("hi", "mr"):
        translated = {s.attrib["name"]: s.text or "" for s in ET.parse(base / f"values-{code}/{name}.xml").getroot()}
        assert original.keys() == translated.keys(), f"Missing {name} entries for {code}"
        for key in original:
            assert Counter(slots.findall(original[key])) == Counter(slots.findall(translated[key])), key
            assert re.search(r"[\u0900-\u097f]", translated[key]), key

# The language preference must not write to recognition settings or trigger/note storage.
preferences = (ROOT / "app/src/main/java/com/bithead/shelter/ui/localization/UiLanguage.kt").read_text(encoding="utf-8")
assert 'putString("ui_language", code)' in preferences
for forbidden in ('putString("speech_language"', 'putString("user_safeword"', 'putString("memo"', 'Locale.setDefault', '.recreate('):
    assert forbidden not in preferences, forbidden
notes = (ROOT / "app/src/main/java/com/bithead/shelter/ui/screens/NotesScreen.kt").read_text(encoding="utf-8")
assert 'Text(note)' in notes and 'SafewordMatcher.matchesExactly(value, safeword)' in notes
assert 'uiText(note)' not in notes, "User notes are content, not interface copy"
print(f"Copy checks passed: {len(rows)} UI entries, {resource_count} walkthrough/mesh resources per locale, placeholders and preference isolation.")
