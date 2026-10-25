# Google account login

Branch: feat/google-account-login, based on the published real-time mesh branch (includes Notes and logo fixes).

## Google Cloud configuration required

1. Configure Google Auth Platform consent and test users in your Google Cloud project.
2. Create an Android OAuth client for package com.bithead.shelter and the installed APK signing certificate SHA-1. Use the retained app/vaani-debug.keystore for this debug build; register production signing certificates separately.
3. Create a Web OAuth client in the same project. Put its public client ID in app/src/main/res/values/account.xml, google_web_client_id. Never put a client secret in the Android app.
4. Rebuild and install. Until configured, the app shows a specific configuration error, not a fake successful login.

## Behavior and data connection

First eligible app setup offers Google sign-in or continuing without an account. Settings provides sign-in and sign-out afterward. Uses Android Credential Manager’s explicit Google button flow. Cancelling, missing accounts, provider failures, malformed profiles and general connection/configuration errors have localized messages. Duplicate requests are disabled while a request is active.

A successful provider response copies name and email into the existing LocalMeshProfileSource via applyAccountProfile. Phone and location are not imported. Name uses the existing 48-character mesh limit; incompatible email/profile data is rejected without replacing the current profile. Import resets all sharing toggles. The existing account-generation observer stops active BLE sharing and reloads fields. Sign-out clears the linked local profile and sharing settings even if clearing the provider session fails. Mesh peers still see self-reported public data, not authenticated Google identities.

Only local profile information is retained. ID tokens are neither persisted, logged nor sent over BLE. This connects a Google-provided profile locally; it does not create a server session, sync evidence, or authorize backend access. If a backend is added, verify ID tokens on that server before granting access.

## Focused manual check

With OAuth configured, sign in during setup and verify Community mesh name/email match. Email must remain hidden until its sharing switch is enabled. Sign out in Settings and confirm mesh fields clear. Check cancellation and retry once. Existing emergency and Notes flows remain available without a Google account.

Reference: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation

Validation: :app:compileDebugKotlin passed. No full tests, APK build, or live Google login was run for this change. OAuth client configuration is still required.
