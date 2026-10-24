# Guided walkthrough and feature help

Implemented on branch feat/guided-walkthrough.

## Behavior

- First eligible safety-screen session offers an introduction. Start enters an eleven-step spotlight tour; Not now, Skip and Finish persist the handled version.
- Settings → Help & onboarding → Replay walkthrough starts again and restores the Settings scroll position on exit.
- Settings → Feature help lists all shared feature explanations; contextual Learn about… buttons replace selected repeated paragraphs.
- The overlay is a separate full-screen modal. Its clear spotlight area does not activate the underlying control.
- The tour navigates by stable lazy-item keys, waits for target composition, and falls back to an explanation if a target is unavailable.
- Notes, camera, blackout, permission prompts and capture/sealing exclude the tour. Recognition is suppressed during the introduction and tour without changing stored mode preferences.

## Shared localized copy

FeatureHelp.kt maps stable feature IDs to resource IDs in res/values/walkthrough.xml. The tour and contextual help resolve the same resources during composition. The file also contains extracted static screen labels; no target languages or unreviewed translations are shipped.

Add future translations under values-<locale>/ using the same resource names. Android locale changes re-resolve strings and restart target measurement while retaining the current step. Do not store resolved strings or use translated titles for control flow. Existing dynamic status strings and some pre-existing compound/list copy still need extraction as part of full-app multilingual work.

| Explanatory area | Shared catalog entry / migration |
| --- | --- |
| Safeword and gesture behavior | SAFEWORD / GESTURE; contextual help in Settings |
| Trusted-contact SMS | CONTACTS; contextual help replaces repeated paragraph |
| Vault storage and export | VAULT; contextual help replaces storage paragraph |
| Photo/video capture and import | CAMERA; contextual help on dashboard and Settings |
| Device authentication | SECURITY; contextual help |
| Location tracking | LOCATION; contextual help; live fix status retained |
| Disguise and return interaction | DISGUISE; contextual help; toggle/action retained |
| Support resources | SUPPORT; contextual help; service-specific context retained |
| Dispatch | DISPATCH; shared description retained beside preview state |
| Decoy call | DECOY; contextual help; scheduled state retained |
| Community map | MAP; tour/global help; sample labels and local-condition warnings retained |
| Integrity and blackout | INTEGRITY / BLACKOUT; global feature help |

Do not strip permission rationale, live recording/listening/sealing status, sample/preview labels, security boundaries or destructive-action confirmations during later UI refinement.

## Deliberate shake and listening cutoff

ShakeTriggerGate accepts three strong alternating impulses on one dominant axis within 900ms, at least 80ms apart, with a 3-second cooldown. The adapter subtracts a time-based gravity estimate. A single bump or low-amplitude repeated movement is rejected. Defaults are intentionally conservative starting values and require real-device calibration.

Gesture recognition has a hard 12-second deadline after readiness. Late readiness/results cannot open an inactive gesture window or activate capture after expiry. Pausing, disabling or entering the tour cancels pending recognition, and repeated shake events do not extend the active window.

Unit tests cover deliberate sequences, cooldown, ordinary-motion amplitudes, isolated impulses, axis changes and reset. Instrumentation covers the tour/replay path without feature side effects, preference persistence and resource fallback.

## Build

Use the checked-in Gradle wrapper with an appropriate JDK and Android SDK. A supplied app/vaani-debug.keystore is preserved; otherwise normal local debug signing applies. Local keys and local.properties are ignored.

~~~bat
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
gradlew.bat :app:connectedDebugAndroidTest
~~~

Physical validation remains required for actual motion traces, microphone indicators, permission prompts, TalkBack, large text, RTL and locale changes, and foreground/disguise transitions. Unit traces cannot establish real-world false-wake rates.

## Verification in this workspace

- Debug app APK and Android instrumentation-test APK built successfully.
- Seven JUnit tests passed: five shake-gate tests and two bounded-window tests.
- Lint ran and reported 402 errors in unchanged source: 401 RestrictedApi findings in checked-in Room-generated implementations, and one missing optional telephony feature declaration in AndroidManifest.xml. No lint errors were reported against the new walkthrough or gesture files. These existing issues were not suppressed.
- Instrumentation tests were compiled, not executed. ADB could not initialize its user tooling directory in this environment, and the emulator acceleration check did not report usable acceleration.
- Physical motion calibration and visual/accessibility/locale-change checks remain outstanding.
- This machine’s JetBrains JDK required the local JVM setting -Djbr.java.io.use.nio=false because its newer file backend incorrectly reported generated workspace files as non-writable. This is a local runner workaround, not an application setting.
