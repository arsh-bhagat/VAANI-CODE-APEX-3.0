# Walkthrough on UIUX/update

This branch ports the guided walkthrough described in WALKTHROUGH_IMPLEMENTATION_MAP.md and walkthrough-porting-data.json into the current screen-based UI. Those two files remain snapshots of the source branch; this document describes the destination implementation.

## Behavior

- The first eligible safety-screen session offers an introduction and eleven spotlight steps.
- The sequence covers navigation, safeword, manual alert, contacts, vault, camera, community map, support, decoy call, disguise, and replay.
- Settings → Help & onboarding provides Replay walkthrough and Feature help.
- Replay returns to Settings and restores the original list position. First-use completion/dismissal returns to Spaces.
- Not now and Skip tour mark the current version handled. Finish also marks it completed. Replay remains available after either choice.
- The modal blocks real controls, locks the vault on entry, and pauses voice/gesture recognition while active without rewriting the user's mode preferences.
- Backgrounding hides the overlay and preserves its step. Camera, recording, sealing, disguise, and blackout interrupt it. A deferred offer in Notes does not activate walkthrough suppression.
- Missing targets show explanatory fallback text and allow the user to continue.

## Destination adaptations

- Retains the existing separate ArmedScreen, VaultScreen, CommunityScreen, SupportScreen, and SettingsScreen files.
- Uses gold spotlight accents and 4dp card corners to match the existing theme.
- Uses Spaces in navigation copy and double-tap in blackout help.
- Each screen uses its own indexed tour list state. The navigation step scrolls to the existing alert panel and highlights the bottom navigation.
- Gives the decoy controls a separate keyed panel so auto-scrolling can position them reliably.
- Restores the Settings scroll once on replay exit rather than again on subsequent eligibility changes.
- Does not port the source branch's new shake detector or listening-window deadline. Gesture help describes this branch's existing trigger.
- Adds only tour/help resources and the shared PERSONAL label, rather than extracting the rest of this branch's screen text.

## Data

All content is local Android resources in app/src/main/res/values/walkthrough.xml. FeatureHelp.kt contains 17 catalog entries and 11 steps. No backend, Room migration, or new production library is required.

SharedPreferences file: vaani_walkthrough. Integer keys: handled_version and completed_version. Current version: 1. The file/key names match the source implementation, so an existing handled version remains handled when using the same installed application data.

The active step and replay scroll position use session-keyed Compose saved state. The process UUID and activity Bundle flag preserve a tour through activity recreation in the same process. After process death an unhandled first-use tour can be offered again; a completed user's interrupted replay is not automatically resumed.

Only default English copy is included. Additional Android locale resource files can reuse the same string IDs and format arguments.

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
