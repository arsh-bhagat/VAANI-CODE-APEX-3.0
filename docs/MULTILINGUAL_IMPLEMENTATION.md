# Interface and speech languages

Settings → Interface language offers English, हिन्दी and मराठी. Hindi and Marathi text is hardcoded and bundled with the application. No translation service, translation model, language download or Google ML Kit dependency is used. Device speech services can still require a speech model; that is independent of interface translation.

`UiLanguage` stores `ui_language` in `shelter_prefs`, defaulting to English. Its Compose provider applies a local resource configuration and recomposes the existing UI without recreating the activity. It leaves `speech_language`, `offline_speech_only`, `user_safeword`, notes, contacts and evidence unchanged. Hindi and Marathi use Android's Devanagari-capable system font without Latin letter spacing.

Bundled copy covers navigation, safety controls, camera/vault dialogs, Notes sample content, support resources, app-owned status messages and the embedded community map. Walkthrough strings have matching `values-hi` and `values-mr` resources. The map keeps its canonical English filter IDs while translating visible text. Dates follow the selected interface locale. Technical identifiers, hashes, phone numbers, stored safewords and user-written content are preserved. Unrecognized model class names and error details returned by device services retain their original text.

The editable catalogs are `scripts/localization/ui-copy.psv` (English / Hindi / Marathi) and `scripts/localization/walkthrough.psv` (resource key / Hindi / Marathi). Preserve numbered placeholders when editing. Regenerate bundled Kotlin, JavaScript and locale XML with:

```powershell
python scripts/localization/generate_copy.py
python scripts/localization/check_copy.py
node scripts/check-map.cjs
```

These commands do not build the app. `UiCopyTest` covers runtime formatting and `UiLanguageTest` covers Compose switching, walkthrough resources, saved preferences and speech/safeword isolation. The authorized v6 debug build and all 34 JVM unit tests passed. The device-based Compose tests have not been run.

After the next authorized install, select हिन्दी in Settings → Interface language, visit all five tabs and replay Help & onboarding. Repeat with मराठी, close and reopen the app to verify persistence, and switch back to English. Confirm that a saved note, trusted contact and safeword remain identical. In Notes, search using the translated sample-note title, then use the original saved safeword to reveal Vaani. Check Devanagari text at large system font sizes for clipping. No recording or SMS is necessary to test language switching.

## Speech settings

Settings → Voice recognition lets users choose the recognition language, inspect support, request a speech model from their device speech service, and explicitly accept a suggested phrase. Choosing a speech language does not change the saved safeword. The existing Unicode literal phrase matcher remains.

`SpeechPreferences` stores `speech_language` and `offline_speech_only` in `shelter_prefs`. An upgrade migrates the former `selected_language` preference to the speech setting and removes that obsolete preference. No translation model is requested. Previously downloaded speech models are owned by the device speech service and remain separate from removed UI translation.

On-device speech only is enabled by default. Android 12/API 31+ devices can use their on-device recognition service when available. Android 13/API 33+ support queries check installed speech languages; Settings can request a speech-model download. Unsupported devices retain manual recording. Turning off on-device-only mode explicitly permits the device speech service to use the network and potentially send microphone audio for recognition.

## Independent protection controls

- Continuous acoustic trigger on: listen for the saved safeword while the app is foregrounded, including when motion detection is also enabled.
- Continuous acoustic trigger off, motion on: keep the microphone off until motion rules detect a candidate, then listen for up to 15 seconds.
- Both off: neither continuous voice recognition nor motion monitoring runs; manual activation remains available.
- A motion-window match can activate the emergency even if the acoustic toggle is off. Timeout cancels that window without starting an incident. Disabling motion cancels its window without changing the acoustic preference.

The motion window gets a fresh recognizer generation so callbacks from the preceding continuous session cannot enter it. Its deadline starts when the microphone becomes ready, with a separate startup timeout. Capture, walkthrough suppression and leaving the foreground suppress recognition and motion callbacks. Continuous listening resumes after the window only when its own toggle is on.

## Source map

- `ai/SpeechPreferences.kt`: speech-language/privacy persistence without translation.
- `ai/LanguageCatalog.kt`: language names, speech locales, suggested phrases and literal matcher.
- `ai/ListeningPolicy.kt`: independent listening, window deadline and deferred activation rules.
- `ui/components/SpeechSettingsCard.kt`: voice-only settings and explicit phrase confirmation.
- `MainActivity.kt`: speech service lifecycle, support checks and independent motion/acoustic preferences.

Unit tests cover phrase matching, motion rules and independent listening/activation policy, including expired windows. Compose tests cover explicit suggested-phrase confirmation. Physical-device testing remains necessary for speech accuracy, permissions, microphone handoff and background/rotation behavior; compilation and unit tests do not establish detection accuracy.
