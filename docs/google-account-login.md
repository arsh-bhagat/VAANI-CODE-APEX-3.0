# Google account login

Current supplied OAuth configuration (25 October 2026):

- Web client configured in `account.xml`: `123776690176-5v5tq8g2cbimvkakrq79is1e5p7au6f9.apps.googleusercontent.com`.
- Android client reported by the user: `123776690176-lbd4dckpge22gpvkf2gk38ldfgb02uka.apps.googleusercontent.com`.
- Android package: `com.bithead.shelter`.
- Current debug signing SHA-1: `E6:0E:4A:AB:25:61:54:6B:49:77:C8:30:F1:50:86:3F:93:C7:11:3E`.

Google Cloud registration has not been independently checked. Vaani-v7.apk has been rebuilt with this Web client ID; the packaged resource and APK signature were verified. Successful phone sign-in remains unconfirmed. Older client IDs in the implementation record describe the backup's history.

For the complete current implementation, OAuth values, lifecycle fix, troubleshooting history and verification status, see [Google account login implementation](GOOGLE-ACCOUNT-LOGIN-IMPLEMENTATION.md).

Integrated into feat/hardcoded_language from the verified Google-login backup at commit 3908556. The source feature branch is feat/google-account-login.

This integration preserves the Hindi/Marathi interface, aligned walkthrough and Bluetooth controls. Account labels, errors and mesh guidance have bundled Hindi/Marathi translations, generated from scripts/localization/account.psv and mesh.psv. The Web client ID is non-translatable. Account setup stays composed across the foreground change caused by Google's picker and must be completed or skipped before the walkthrough.

The integration was reviewed again on 31 October 2026 against source branch commit `3908556`. The Google panel, local account storage, Bluetooth controller, mesh models, mesh panel and supplied logo were already present in the working tree. The missing Notes unlock helper, keyboard handling and regression tests were incorporated while preserving localized Notes search and interface text. The current supplied Web client ID was retained rather than reverting to the older source-branch value.

Validation results for this combined working tree are recorded in [integration validation](GOOGLE-LOGIN-INTEGRATION-VALIDATION.md). Phone sign-in remains an outstanding acceptance check.

## Google Cloud configuration

1. Configure Google Auth Platform consent and test users in your Google Cloud project.
2. Create an Android OAuth client for package com.bithead.shelter and the installed APK signing certificate SHA-1. Use the retained app/vaani-debug.keystore for this debug build; register production signing certificates separately.
3. The supplied Web OAuth client ID is configured in app/src/main/res/values/account.xml, google_web_client_id. The Android OAuth client must belong to the same project. Never put a client secret in the Android app.
4. Rebuild and install. Until configured, the app shows a specific configuration error, not a fake successful login.

## Behavior and data connection

First eligible app setup offers Google sign-in or continuing without an account. Settings provides sign-in and sign-out afterward. Uses Android Credential Manager’s explicit Google button flow. Cancelling, missing accounts, provider failures, malformed profiles and general connection/configuration errors have localized messages. Duplicate requests are disabled while a request is active.

A successful provider response copies name and email into the existing LocalMeshProfileSource via applyAccountProfile. Phone and location are not imported. Name uses the existing 48-character mesh limit; incompatible email/profile data is rejected without replacing the current profile. Import resets all sharing toggles. The existing account-generation observer stops active BLE sharing and reloads fields. Sign-out clears the linked local profile and sharing settings even if clearing the provider session fails. Mesh peers still see self-reported public data, not authenticated Google identities.

Only local profile information is retained. ID tokens are neither persisted, logged nor sent over BLE. This connects a Google-provided profile locally; it does not create a server session, sync evidence, or authorize backend access. If a backend is added, verify ID tokens on that server before granting access.

## Focused manual check

With OAuth configured, sign in during setup and verify Community mesh name/email match. Email must remain hidden until its sharing switch is enabled. Sign out in Settings and confirm mesh fields clear. Check cancellation and retry once. Existing emergency and Notes flows remain available without a Google account.

Reference: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation

Validation: focused Kotlin compilation and subsequent debug APK builds passed. Full tests were not rerun. The user reported recreating the Android OAuth client on 25 October 2026; successful phone sign-in afterward remains unconfirmed. See the implementation record for details.
