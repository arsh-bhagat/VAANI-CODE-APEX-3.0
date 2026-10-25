# Real-time Bluetooth mesh: nearby-user discovery

This feature originated on feat/realtime-bluetooth-mesh and is integrated into feat/hardcoded_language from the verified ad5e47a backup. It provides direct Bluetooth LE discovery and profile exchange, not Bluetooth SIG Mesh, multi-hop routing, emergency message relay, or guaranteed delivery.

## Localized-branch integration

The saved transport, profile models, preferences and protocol tests are restored without changing the wire format. Bluetooth permissions are merged into the current manifest, retaining its v6 label, local evidence services and privacy settings. Existing Gradle dependencies and TensorFlow Lite 2.13 runtime remain unchanged.

Community has a pinned entry button and a separate scrollable nearby-user form. Back returns to the map. Names, contact fields and received profiles stay unmodified; all 41 mesh UI strings are bundled in English, Hindi and Marathi. Edit `scripts/localization/mesh.psv` and regenerate with `scripts/localization/generate_copy.py`.

During a walkthrough, Community closes the mesh pane, hides its pinned entry and shows the map so the existing eleven-step target alignment is preserved. The panel disposes and stops its controller. Sample map-area popups are suppressed while the nearby-user pane is open, avoiding interruption during optional location refreshes.

No build or radio test was run for this integration. Static checks cover resource/placeholder parity, restored source identity, Bluetooth manifest declarations, map contracts and all eleven walkthrough anchors. `MeshNavigationTest` adds UI coverage for language changes, pane navigation and yielding to a walkthrough without starting Bluetooth. Instrumentation and real two-phone testing remain pending.

## Use

Open Community and tap the large gold **Real-time mesh** button pinned above the map on both phones. Use **Back to community map** to return. Choose the public name, optionally enter phone/email contact details, select contact/location sharing if wanted, and press Start nearby mesh. Accept the relevant Android permissions and Bluetooth-enable prompt.

Both devices must participate and support simultaneous BLE scanning, connectable advertising and GATT serving. Unstarted, non-consenting or out-of-range app installations cannot be discovered. Sessions stop when leaving the mesh pane or when the app is no longer visible; rotation/recreation also ends the old transport. This is not a background/locked-screen mesh service.

The live roster displays name, optional phone/email contact details, optional approximate coordinates and accuracy, and the last received Bluetooth signal time. Dialer and maps open only on explicit taps. No automatic call or SMS is sent.

Profiles are refreshed about every 10 seconds while discovery results arrive. Peers expire after 30 seconds without signals. Timing is best-effort and depends on Android/radio scheduling; it is not a hard real-time guarantee.

## Architecture

- MeshModels.kt: consent projection, location freshness/approximation, bounded binary wire codec, in-memory roster.
- MeshProfileSource.kt: private local profile preferences plus a provider-independent account import/sign-out interface.
- BluetoothMeshController.kt: fixed service UUID advertising/scanning; GATT server/client; serial reads; MTU negotiation and long-read offsets; bounded retries, timeouts and session-generation guards.
- RealtimeMeshPanel.kt: public-profile form, opt-in start/stop, permission flow, lifecycle ownership and nearby-user cards.
- CommunityScreen.kt: separate map and mesh views. Existing map risk overlays remain sample data.

The characteristic is a read-only, versioned profile. Advertising contains the service UUID, not a name, phone number, Google identifier or location. Selected public details are fetched over GATT. Packets are limited to 512 bytes, names to 48 characters, phone fields to 32 permitted characters, and public email to 120 validated characters. Malformed/truncated/unknown-version packets are rejected.

Only one outgoing GATT read is active at a time. Each connection has an eight-second timeout and per-peer refresh backoff. Startup is bounded; discovery and roster caches are capped at 40 entries. Nearby identities and Bluetooth addresses are never written to disk. Public session UUIDs rotate on each start.

Location comes from the app's own location fix, not an RSSI-to-distance estimate. It is shared only by consent, rounded to three decimal places with a minimum declared uncertainty of 150 metres. Missing accuracy, invalid coordinates, fixes older than two minutes or excessively future-dated fixes are withheld. Approximate Android grants may have much larger uncertainty.

## Privacy and trust

Starting mesh deliberately makes the chosen public profile readable to nearby devices that implement the protocol. The GATT characteristic is not encrypted or authenticated; a determined nearby client need not be the genuine VAANI application. Names, contact details and GPS claims are self-reported. Do not rely on them as authenticated identities or verified safety information.

Phone, email and location sharing default off. Stop ends further advertising/serving and clears the local roster, but cannot retract information already received by another device. A public email is exchanged only when its separate sharing option is enabled. No private evidence, contact list, Google subject/account ID or authentication token enters the protocol.

This first version does not pair/bond devices, implement end-to-end encryption, verify remote Google identities, or relay data across intermediate phones. Those need separate protocol/security work rather than being implied by the word mesh.

## Google-login branch handoff

Google sign-in is intentionally not implemented in this branch. Until that branch exists, users supply a local public name and optional phone/email contact details. The profile preferences cache the same public fields that a future sign-in adapter will populate.

After authenticating the local account in the future branch:

1. Map the chosen public name, optional contact email and any separately authorized/user-entered phone number to AccountProfileSeed.
2. Call MeshProfileSource.applyAccountProfile(seed). This resets sharing flags instead of silently enabling contact/location broadcast.
3. Ask the user to review public fields and explicitly start a new mesh session.
4. On sign-out, call clearAccountProfile(). Account-generation observers stop the active UI transport, clear/reload the profile fields and require fresh consent/start.
5. Keep credentials, Google ID tokens and account identifiers outside the BLE model. Basic sign-in must not be assumed to supply a phone number.
6. Local Google login alone does not authenticate a received BLE profile. Remote verification requires a separate signed-public-profile design/backend verification, not forwarding raw Google tokens.

Account imports/sign-out use a preference generation signal, so another repository instance in the same app process can notify the current mesh UI. The future login branch should invoke changes on the main/UI lifecycle path and retain these consent/reset boundaries.

## Permissions and platform references

Android 12+ requires Nearby devices permissions for scanning, advertising and connecting. This location-capable discovery feature also requests precise location rather than asserting neverForLocation; fine/coarse permissions are requested together on modern Android. Older versions require fine location for BLE discovery. This implementation checks that device location services are enabled before scanning. Granting access does not enable broadcasting coordinates: the separate location-sharing consent remains mandatory. Existing app location declarations remain in the manifest. BLE support is optional so unsupported phones retain the rest of VAANI.

No neverForLocation assertion is made for this location-capable feature. No background location permission, Accessibility, device-admin or background service has been added.

- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [GATT server read responses](https://developer.android.com/reference/android/bluetooth/BluetoothGattServer#sendResponse(android.bluetooth.BluetoothDevice,int,int,int,byte[]))
- [GATT client and MTU handling](https://developer.android.com/reference/android/bluetooth/BluetoothGatt)

## Validation

Unit tests cover consent filtering, approximate/stale locations, Unicode packet round trips, malformed/oversized/version-mismatched packets, unsafe field validation, and roster expiry/caps. An instrumentation test covers future account import/sign-out preferences.

Build checks:

~~~bat
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
~~~

Required two-phone checks before claiming radio reliability:

1. Start with two distinct names; grant permissions; verify mutual discovery.
2. Start with all sharing flags off; verify neither phone/email nor coordinates appear.
3. Opt in to phone, email and location separately; verify each field and fresh GPS updates.
4. Test long Unicode names and low/default versus negotiated MTU to validate complete long reads on both chipsets.
5. Move out of range, disable Bluetooth, revoke permissions, stop, rotate, switch panes and background the app; confirm cleanup, expiry and no unexpected restart.
6. Check duplicate advertisements, rapid stop/start, failed connections, unsupported advertising and denied permissions.
7. Inject malformed profiles; verify rejection and continued operation.
8. Import a future account seed and sign out while active; verify radio shutdown and consent reset.
9. Confirm mesh neither starts recording nor sends emergency alerts.
10. Measure battery use, discovery/update latency and practical range across the target Android/OEM matrix.

A single phone/emulator or unit tests cannot prove BLE interoperability, location quality or real-world range. Hardware testing remains a release gate.

## Historical build verification on the original mesh branch

The app and instrumentation-test APKs build successfully. Fifteen JUnit tests pass: eight mesh-model/protocol tests, five Notes-unlock tests and two existing gesture-gate tests. Instrumentation tests compiled but were not executed on a device. Actual two-phone discovery, GATT interoperability, radio range, battery behavior and UI/permission flows remain unverified until the physical test plan above is run. The approved Notes and black-logo fixes are included as an ancestor commit.
