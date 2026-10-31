# Google account login — implementation record

Current validation (31 October 2026): the combined integration builds successfully, all 54 JVM tests pass, the device-test APK builds, and lint reports 0 errors and 58 warnings. Device tests and real Google sign-in remain unverified. See [integration validation](GOOGLE-LOGIN-INTEGRATION-VALIDATION.md). The dated notes below preserve earlier implementation history.

Integration note (25 October 2026): restored from the checksum-verified 3908556 backup onto feat/hardcoded_language. Dependencies, setup latch, Settings panel and account-aware mesh profile were merged while preserving language and walkthrough changes. Bundled Hindi/Marathi account resources are now included. Regression tests cover setup skip/foreground eligibility, account marker isolation and translated labels; they have not been executed. No build was started for this integration. Historical build/device results below describe the original feature branch.

Documented: 25 October 2026. Feature branch: feat/google-account-login, created from feat/realtime-bluetooth-mesh, which includes the approved Notes unlock and VAANI logo fixes from UIUX/update.

## 1. Purpose and current status

This feature lets a user select a Google account during setup or from Settings and use its name and email to populate VAANI’s Community Bluetooth profile. Users can continue without signing in. Nearby discovery and sharing remain separate actions requiring explicit consent.

Implementation, Kotlin compilation and debug APK assembly have succeeded. The implementation was pushed to feat/google-account-login. On-device login previously failed with GetCredentialCustomException / 28404 and an additional reported code 16. The user subsequently recreated the missing Android OAuth client. Successful phone sign-in after that change has **not yet been confirmed**. Do not describe the feature as fully verified until that check succeeds.

This is a local Google profile connection. It does not create a backend session, validate tokens on a server, upload evidence, synchronize accounts between phones, or authenticate other mesh peers.

## 2. Files and responsibilities

| File | Responsibility |
| --- | --- |
| [app/build.gradle.kts](../app/build.gradle.kts) | Credential Manager and Google ID dependencies; Android package and signing configuration. |
| [GoogleAccountPanel.kt](../app/src/main/java/com/bithead/shelter/ui/screens/GoogleAccountPanel.kt) | Account UI, Google picker request, response checks, profile import, sign-out, error messages and diagnostics. |
| [VaaniApp.kt](../app/src/main/java/com/bithead/shelter/ui/VaaniApp.kt) | Initial account setup, completion preference, walkthrough ordering and lifecycle visibility fix. |
| [SettingsScreen.kt](../app/src/main/java/com/bithead/shelter/ui/screens/SettingsScreen.kt) | Reuses GoogleAccountPanel for later sign-in and sign-out. |
| [MeshProfileSource.kt](../app/src/main/java/com/bithead/shelter/mesh/MeshProfileSource.kt) | Local profile persistence, account marker and account-change notifications. |
| [RealtimeMeshPanel.kt](../app/src/main/java/com/bithead/shelter/ui/screens/RealtimeMeshPanel.kt) | Loads Community fields; observes account changes, stops sharing and reloads values. |
| [MeshModels.kt](../app/src/main/java/com/bithead/shelter/mesh/MeshModels.kt) | Existing validation and consent-filtered public BLE profile. |
| [account.xml](../app/src/main/res/values/account.xml) | Web client ID and translatable account UI/error strings. |
| [mesh.xml](../app/src/main/res/values/mesh.xml) | Community guidance explaining the Google profile connection. |

Dependencies added: androidx.credentials:credentials:1.3.0, androidx.credentials:credentials-play-services-auth:1.3.0 and com.google.android.libraries.identity.googleid:googleid:1.1.1. Current app configuration is minSdk 26, targetSdk 35 and applicationId com.bithead.shelter. No Firebase dependency or google-services.json was added for this feature.

## 3. OAuth configuration

Two distinct OAuth client registrations are needed in the same Google Cloud project. Client IDs are public configuration; a client secret is not used in the Android app.

| Setting | Current value / role |
| --- | --- |
| Google Cloud project number inferred from supplied client IDs | 765543613406; project display name was not independently inspected. |
| Android package | com.bithead.shelter |
| Debug signing certificate SHA-1 | EF:4C:EA:C0:94:90:39:EF:67:A8:62:41:6D:3E:AF:76:45:0D:E6:F4 |
| Configured Web application client ID | 765543613406-202t5go52p0rmhkos4gtkh3135utojcs.apps.googleusercontent.com |
| Recreated Android client ID, reported by user | 765543613406-ica0fc88akcefjsh068i6jpm1etm1td9.apps.googleusercontent.com |
| Local debug signing key | app/vaani-debug.keystore; development file, not a distributable project asset. |

The Web ID is read from R.string.google_web_client_id and passed to GetSignInWithGoogleOption.Builder. The Android ID is registered in Google Cloud alongside the package/signature; it is not substituted into that builder.

Setup procedure:

1. Select the Google Cloud project containing the Web client.
2. Configure Google Auth Platform Branding and Audience. Add testing accounts under Test users where the project’s access settings require them.
3. Under Clients, retain the Web application client. Native sign-in does not require adding JavaScript origins or redirect URIs to this implementation.
4. Create an Android client using the package and SHA-1 above in the same project. Save it.
5. Keep the Web client ID in account.xml. Changes to the Android client registration alone do not require rebuilding the APK.
6. Install the latest debug APK on a phone with Google Play services, a Google account and internet access, then test sign-in.

The SHA-1 above belongs to this retained debug key. A differently signed release or Play-distributed build needs its own matching Android OAuth registration. Never regenerate a development signing key casually: both update compatibility and OAuth configuration depend on it.

A Web client secret was supplied during setup but was not added to the repository or APK. It is deliberately omitted from this document. The user was advised to reset it after disclosure.

## 4. User flow and setup ordering

VaaniApp reads vaani_account_setup / done. On the first eligible entry, it shows GoogleAccountPanel before the walkthrough. The user can sign in or choose Continue without an account. Continuing records done = true; this marks setup handled, not successful authentication. Signing in displays the linked email and changes the primary action to Sign out. In setup, the user then presses OK to continue.

Settings also embeds GoogleAccountPanel, so a user who skipped setup can connect later. Signing out does not reset the setup-completed preference. It clears the linked profile while leaving ordinary app access available.

The panel disables its action and continuation buttons while a request is running and restores them in finally. There are no automatic retry loops. Emergency, sealing, disguise and blackout conditions retain their existing priority over account setup.

## 5. Sign-in request and response handling

1. Create CredentialManager with the current UI context and launch from rememberCoroutineScope after the user presses Sign in with Google.
2. Read and trim the configured Web client ID. Check that it ends in .apps.googleusercontent.com. This is only a basic format check; it cannot verify the client type or Cloud registration.
3. Build GetSignInWithGoogleOption, add it to GetCredentialRequest and call manager.getCredential with the UI context.
4. Require CustomCredential with GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.
5. Parse using GoogleIdTokenCredential.createFrom(result.data).
6. Read google.id as the email. Use the trimmed display name, falling back to the email prefix if the name is missing.
7. Reject a blank/invalid email or a name incompatible with mesh validation. The name is limited to 48 characters; accepted emails must meet the existing mesh format and 120-character limit.
8. Call applyAccountProfile(AccountProfileSeed(name, email = email)). The UI observes the account change and updates its signed-in presentation.

An unsuccessful or malformed response does not import a new profile. The ID token is not saved, logged or transported over BLE. Parsing the credential is not server-side token verification; this implementation must not be used as backend authorization without a server verification flow.

## 6. Community data connection

GoogleAccountPanel → AccountProfileSeed → LocalMeshProfileSource.applyAccountProfile → MeshOwnerProfile → Community form → consent-filtered public profile → BLE exchange.

| Data | Local use | Nearby sharing |
| --- | --- | --- |
| Display name | Imported and truncated to the mesh name limit. Editable in the Community form. | Included in the public profile when the user explicitly starts mesh. |
| Email | Imported as profile email and account_email marker. | Included only when share_email is enabled. |
| Phone | Not obtained from this Google flow. Existing phone data is cleared on account import; the user may enter it again. | Included only when share_phone is enabled. |
| Location | Not obtained from Google login. Existing device-location flow remains separate. | Included only with location sharing, permissions and existing freshness/approximation rules. |
| Token / Google identity credential | Transient response handling only. | Never included. |
| Account photo | Not imported or persisted. | Not included. |

Import creates a fresh MeshOwnerProfile with all sharing flags false, rather than carrying consent from a previous account. It writes the account_email marker and increments account_generation. A mounted RealtimeMeshPanel observes that generation, cancels pending starts, stops its Bluetooth controller and reloads the form. A newly opened mesh panel reads the stored profile.

MeshProfileSource uses private SharedPreferences named vaani_mesh_profile. Relevant keys are name, email, phone, share_email, share_phone, share_location, account_email and account_generation. These are local preferences, not an encrypted account vault or remote user database. Account generation is a change signal, not an identity or authentication token.

The signed-in email marker and editable public-profile email are separate keys. Editing the public field does not change which account the Settings panel displays. Nearby users still receive self-reported data; Google login does not prove ownership of a BLE peer’s advertised profile.

## 7. Sign-out

The panel calls clearAccountProfile first. That clears the entire mesh profile preference file while retaining an incremented generation value to notify observers. Imported name/email, user-entered phone and sharing flags are therefore cleared, and a mounted mesh session stops.

Next it calls clearCredentialState(ClearCredentialStateRequest()). If the provider cannot reset its session, local sign-out remains effective and the UI displays a specific warning. This is not revocation of Google account consent, deletion of the Google account, or deletion of evidence and trusted contacts. The user can press Sign in with Google again to connect another account.

## 8. Lifecycle cancellation defect and correction

The first implementation made accountSetupVisible depend directly on walkthroughEligible. MainActivity computes that eligibility using foreground. Opening Google’s external picker pauses MainActivity, which sets foreground = false. That caused Compose to remove the setup panel and cancel the coroutine running getCredential. This could produce a request-cancelled result before the provider finished.

The correction introduces accountSetupStarted with rememberSaveable. Once eligibility permits setup, that state is latched. Setup visibility no longer depends on every subsequent foreground transition, so the panel and request remain composed while Google’s picker is open. Completing setup or entering an overriding emergency/disguise state can still remove it intentionally. Normal recomposition does not remove the request; full Activity/process recreation during sign-in has not been verified.

The phrase v2 in the system cancellation message comes from the current application label in AndroidManifest.xml; it is not a Google error code. This work did not rename the app.

## 9. Errors and diagnostic behavior

| Condition | Current handling |
| --- | --- |
| Missing/malformed configured client ID | Configuration message before opening the picker. |
| GetCredentialCancellationException | Cancellation message and safe diagnostic. No automatic retry; cancellation may have technical causes. |
| NoCredentialException | Account availability guidance. |
| GetCredentialProviderConfigurationException | Google Play services/provider guidance. |
| Unexpected credential or GoogleIdTokenParsingException | Invalid-profile response message. |
| GetCredentialException mentioning 28444, developer console or developer_error | OAuth-registration rejection guidance. |
| Provider message explicitly mentioning network or connection | Connection-failure guidance. |
| Other credential failures | Generic failure plus diagnostic category/type and any extractable numeric status. |
| Other exceptions | Generic failure plus exception class. |
| Provider state-clear failure during sign-out | Local sign-out succeeds; provider-reset warning is shown. |

accountDiagnostic extracts the first bracketed numeric code, or a supported colon-delimited code, and combines it with the exception class and a bounded, filtered provider type. It does not display raw provider text or retain diagnostic history. The UI stores these values in Compose state only.

User-reported codes were 28404 and 16. They do not by themselves establish one root cause. The debug sequence confirmed an Android client ID had initially been used where the Web ID belonged; that was corrected. Later the user reported deleting the Android registration and then recreating it with the new ID recorded above. The remaining sign-in result after recreation is pending. Do not record either code as conclusively a network fault or conclusively fixed.

## 10. Validation and build delivery

Previously completed checks: focused compileDebugKotlin passed; assembleDebug passed for the configuration, lifecycle and diagnostic updates. The signing certificate of an earlier Google-login APK was checked against the SHA-1 listed above. No full test suite was rerun for this feature, following the user’s limited-credit request. No device instrumentation test or successful real Google login has been demonstrated.

Latest delivered APK filename: VAANI-google-login-diagnostics-debug.apk, in the workspace outputs/google-account-login folder. It contains the corrected Web ID, lifecycle correction and diagnostic handling. APKs and the local signing key are not included as source changes in this documentation update.

Build from the Android project directory using its Gradle wrapper: gradlew.bat :app:assembleDebug. This workstation’s work/build-uiux.cmd sets its local JDK and filesystem compatibility flag; it is environment-specific, not part of Google authentication. The debug APK is generated under app/build/outputs/apk/debug/app-debug.apk.

Minimum remaining phone checks:

1. With both OAuth clients present, sign in once using the latest APK and confirm that the linked email appears.
2. Continue into Community → Real-time mesh. Verify the name/email populate and sharing toggles remain off.
3. Enable email sharing explicitly and start mesh on two phones; confirm only the selected details are exchanged.
4. Stop mesh and sign out. Verify imported fields are cleared and account selection is available again.
5. If sign-in fails, capture the diagnostic line and verify the actual installed APK signing certificate, Cloud project/client registration and phone account/network state before another speculative rebuild.

These are pending acceptance checks, not claims that they have passed.

## 11. Boundaries and maintenance notes

All account text is referenced through Android string resources, with bundled English, Hindi and Marathi resources in this integration. The OAuth client ID is marked non-translatable and appears only in the default resources.

Account connection persists locally across restarts and does not actively detect remote Google revocation or expiry. A persisted email marker is not a verified live session. Mesh profile data can be edited locally. The existing BLE implementation remains direct, foreground discovery rather than authenticated multi-hop routing. No server token validation, Firebase auth, cloud profile storage or Google phone-number lookup was introduced.

If a backend is introduced later, design server-side token verification and session handling before protecting remote data with this login result. Keep identity credentials out of MeshOwnerProfile and BLE payloads, and retain explicit per-field sharing consent on every account change.

## References

- [Google Credential Manager sign-in implementation](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Google Android sign-in setup codelab](https://codelabs.developers.google.com/sign-in-with-google-android)
- [Credential Manager troubleshooting](https://developer.android.com/identity/sign-in/credential-manager-troubleshooting-guide)

These references explain the SDK/setup. The implementation and verification status above are based on the checked-out code and this project’s build/debug record.
