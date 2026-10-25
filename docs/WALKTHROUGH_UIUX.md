# Walkthrough on UIUX/update

This branch ports the guided walkthrough described in WALKTHROUGH_IMPLEMENTATION_MAP.md and walkthrough-porting-data.json into the current screen-based UI. Those two files remain snapshots of the source branch; this document describes the destination implementation.

## Behavior

- The first eligible safety-screen session offers an introduction and eleven spotlight steps.
- The sequence covers navigation, safeword, manual alert, contacts, vault, camera, community map, support, decoy call, disguise, and replay.
- Settings → Help & onboarding provides Replay walkthrough and Feature help.
- Replay returns to Settings and restores the original list position. First-use completion/dismissal returns to Vaani.
- Not now and Skip tour mark the current version handled. Finish also marks it completed. Replay remains available after either choice.
- The modal blocks real controls, locks the vault on entry, and pauses voice/gesture recognition while active without rewriting the user's mode preferences.
- Backgrounding hides the overlay and preserves its step. Camera, recording, sealing, disguise, and blackout interrupt it. A deferred offer in Notes does not activate walkthrough suppression.
- Missing targets show explanatory fallback text and allow the user to continue.

## Destination adaptations

- Retains the existing separate ArmedScreen, VaultScreen, CommunityScreen, SupportScreen, and SettingsScreen files.
- Uses gold spotlight accents and 4dp card corners to match the existing theme.
- Uses Vaani in navigation copy and double-tap in blackout help.
- Each screen uses its own indexed tour list state. The navigation step highlights the actual icons-and-labels row, excluding the Android navigation inset.
- Gives the decoy controls a separate keyed panel so auto-scrolling can position them reliably.
- Restores the Settings scroll once on replay exit rather than again on subsequent eligibility changes.
- Does not port the source branch's new shake detector or listening-window deadline. Gesture help describes this branch's existing trigger.
- Adds only tour/help resources and the shared PERSONAL label, rather than extracting the rest of this branch's screen text.

## Data

All content is local Android resources in app/src/main/res/values/walkthrough.xml. FeatureHelp.kt contains 17 catalog entries and 11 steps. No backend, Room migration, or new production library is required.

SharedPreferences file: vaani_walkthrough. Integer keys: handled_version and completed_version. Current version: 1. The file/key names match the source implementation, so an existing handled version remains handled when using the same installed application data.

The active step and replay scroll position use session-keyed Compose saved state. The process UUID and activity Bundle flag preserve a tour through activity recreation in the same process. After process death an unhandled first-use tour can be offered again; a completed user's interrupted replay is not automatically resumed.

English, Hindi and Marathi copy use matching Android string IDs and format arguments.

## Spotlight alignment correction

The walkthrough now shares the app's Compose root instead of using a separate dialog window. Targets keep their full measured bounds in root coordinates; the overlay converts those bounds to its own local origin. This removes the status-bar offset and the former arbitrary 64dp crop on tall features.

Cards are measured against the available space above or below the actual target and placed next to it with room for the pointer. System bars and display cutouts stay outside the card area. Descriptions scroll independently while Back, Next/Finish and Skip stay visible. In a short window, the highlight is limited to the portion of the real target outside the card's reserved area.

Auto-scroll waits for the selected tab and its target to lay out, then aligns the actual control within the content viewport. Contacts highlight the entry form; decoy highlights the scheduling buttons. All eleven steps retain unique anchors and stable scroll keys.

`WalkthroughPlacementTest` covers all eleven target sizes, landscape constraints and unavailable targets. `WalkthroughAlignmentTest` checks all eleven steps with an offset host root and targets taller than the old cropped strip. `WalkthroughTest` additionally checks actual-tour card/highlight separation and passive touch handling. These new checks have not been compiled or run on a device; no build was requested for this correction. `python scripts/check-walkthrough.py` validates anchor wiring without building.

## Validation

Instrumentation coverage is in WalkthroughTest and WalkthroughPreferencesTest: full tour/replay without feature actions, dismissal/help, background eligibility/resume/Back, deferred Notes offer, preference persistence, and default resource fallback.

These Compose tests exercise VaaniApp with fake host callbacks. Actual microphone/sensor suppression, Android permission dialogs, visual layout, TalkBack, large text, and rotation still require device checks. ADB reported no connected device or emulator during implementation.

Build checks use the project's Gradle wrapper with a compatible JDK and SDK:

```text
gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebugAndroidTest
gradlew.bat :app:connectedDebugAndroidTest
```

The second command requires a device/emulator. The source branch's historical validation results are not results for this port.

Verified for this port on 2026-10-24:

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` completed successfully with the installed Microsoft JDK 21.
- Both existing JerkTriggerGate unit tests passed with zero failures/errors.
- The six walkthrough instrumentation test methods compiled and the test APK was packaged; they were not executed because no Android device/emulator was attached.
- Static validation found all eleven step anchors and their spotlight modifiers, resolved the walkthrough string references, and found no duplicate default string resource names. `git diff --check` passed.
- Application APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

The JetBrains IDE runtime lacks jlink on this machine; use the full installed JDK for builds. No project JDK setting was changed.
