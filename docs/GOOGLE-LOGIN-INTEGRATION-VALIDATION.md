# Google login integration validation

Date: 31 October 2026. Target: `feat/hardcoded_language`. Source: `feat/google-account-login` at `3908556`.

The existing uncommitted Google integration was preserved and compared with the source branch. Its account storage, Bluetooth controller, mesh models, mesh panel and logo matched the source. The Google panel retains the source behavior, with its diagnostic helper made internal for unit testing. The supplied Web client ID already present in this checkout was retained. Hindi/Marathi UI, localized account messages, motion features and walkthrough alignment were preserved.

The source branch's missing Notes unlock helper and tests were incorporated. Notes now clears focus and hides the keyboard on unlock, supports the keyboard Search action, and preserves localized search and interface text. A device regression test covers opening the safety interface without activating feature actions.

Lint 8.5.2 reported `RememberReturnType` errors in two existing device tests despite their lambdas constructing `WalkthroughScope`, including after adding explicit type arguments. The tests compile successfully with the non-Unit types. Explicit `remember<WalkthroughScope>` type arguments and narrowly scoped, documented `SuppressLint` annotations were added to these two test methods. No production lint checks were disabled.

The validated integration is being committed for publication to `main`. A before-change patch and copies of touched existing integration files are retained locally under `.android-run/google-login-integration-20261031/`; these local backups are not included in the commit.

## Validation

- Debug app assembly passed.
- Device-test APK assembly passed; the instrumented tests were compiled but not executed.
- Android lint passed with 0 errors and 58 warnings after the two scoped test-only false-positive suppressions.
- All 54 JVM unit tests passed, with no failures, errors or skips. These include four Google diagnostic tests and five Notes unlock tests, along with existing mesh, motion, localization, listening and walkthrough tests.
- Bundled localization checks passed: 495 UI entries and 111 walkthrough/mesh/account resources per locale, including placeholder parity and preference isolation.
- Walkthrough wiring and map checks passed.
- `git diff --check` passed.
- APK signature verification passed using the existing debug key. Certificate SHA-1: `E6:0E:4A:AB:25:61:54:6B:49:77:C8:30:F1:50:86:3F:93:C7:11:3E`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `e75368e140e6f089b78e2da294b98a4e96d68b3a18803fe8171f327e47afa3ad`.

Build command, using the local JDK 21 and SDK:

```powershell
.\gradlew.bat '-Dorg.gradle.vfs.watch=false' :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

## Device acceptance still required

ADB reported no connected devices, and no emulator AVD was available. Device instrumentation tests were therefore not executed. JVM tests and APK assembly do not verify a real Google picker, OAuth Cloud registration, hardware Bluetooth exchange, or phone lifecycle behavior.

On a configured phone, verify Google sign-in during setup, cancel/retry, continuing without an account, and later sign-in/sign-out from Settings. Confirm name/email reach Community while sharing remains off. Confirm sign-out clears the profile and stops active sharing. Check the picker pause/resume behavior, Hindi/Marathi account labels, Notes unlock and walkthrough ordering.

Google Cloud registration and successful real Google sign-in remain unconfirmed.
