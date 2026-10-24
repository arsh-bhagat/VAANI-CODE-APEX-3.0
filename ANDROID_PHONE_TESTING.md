# Testing VAANI on a phone

The ready-to-transfer test build is `VAANI-debug.apk` in the project root. Gradle also writes it to `app/build/outputs/apk/debug/app-debug.apk`. It runs on Android 8.0 (API 26) or newer. The phone does not need Android Studio or the Android SDK.

1. Transfer the APK to the phone using USB or your usual file transfer method.
2. Open it in the phone's Files app and allow installation from that app when Android asks.
3. Launch VAANI and grant the permissions needed for the features you want to test.
4. Start with Settings → **Test interface quietly**. This checks the interface without starting recording or sending messages.
5. Check all five tabs, Notes disguise, memo persistence, map filters, and blackout. Double-tap the black screen to return. Search for `vaani` or `##` in Notes to leave the disguise.
6. For a recording test, hold the activation control for two seconds, record briefly, and stop and seal. **Activation can send real SMS to configured trusted contacts.** Arrange the test with those contacts beforehand.
7. Unlock the vault using the phone's authentication, play the sealed recording, and verify the hash chain. Also check private photo/video capture and import.

Map tiles need internet. Community routes and responder markers are illustrative sample data. Police/NGO dispatch, live mesh relay, and hosted tracking links are not connected. The recording indicator's waveform is decorative; the timer uses the active incident's actual start timestamp. Recognition availability depends on the phone.

This APK uses a local debug signing key. Keep `app/vaani-debug.keystore` to sign future test builds consistently. If an older install uses a different key, Android will reject the update. Export any evidence you need before choosing to uninstall an old build, because uninstalling removes its local data.

## Rebuild on this computer

The local SDK is in `.android-sdk/`; `local.properties` points Gradle to it. Neither the SDK nor the signing key is committed.

With JDK 17 or newer configured as `JAVA_HOME`, run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
node scripts/check-map.cjs
```

Device checks above still need to be performed on a phone; build and automated checks cannot verify microphone hardware, cellular delivery, biometrics, or gesture sensitivity.
