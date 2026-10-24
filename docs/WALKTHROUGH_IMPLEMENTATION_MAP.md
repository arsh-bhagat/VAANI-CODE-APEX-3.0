# Walkthrough implementation and porting map

Source: `feat/walkthrough-multilingual-shake`, commit `58c794fc8988b0dd0f7072efb18f4214904b5a78`.
Analyzed from source on 2026-10-24. This is an implementation inventory for porting to another branch. No application code was changed and no new builds or tests were run for this inventory.

## 1. What the feature consists of

The implementation combines three related UI capabilities:

1. A first-use introduction followed by an eleven-step modal spotlight tour.
2. A Settings action that replays the introduction and tour.
3. Shared feature explanations, shown either in a full help catalog or in individual contextual dialogs.

The branch also changes shake recognition, its listening deadline, and many existing UI strings. Those changes are bundled into the same commit but are separable from the walkthrough.

There is no walkthrough server, API, Room table, downloaded content, analytics event pipeline, image asset, or third-party tour library. Content is compiled into Android string resources. Long-term state uses SharedPreferences; temporary state uses Compose state and the activity saved-state Bundle.

## 2. Source-file inventory

All paths below are relative to the repository root.

| File | Responsibility | Porting treatment |
| --- | --- | --- |
| `app/src/main/java/com/bithead/shelter/data/WalkthroughPreferences.kt` | Versioned offer/completion persistence | Copy; adapt package if necessary |
| `app/src/main/java/com/bithead/shelter/ui/walkthrough/FeatureHelp.kt` | 17 feature IDs and resource references; 11 ordered step definitions | Copy; adapt destinations and lazy-item keys |
| `app/src/main/java/com/bithead/shelter/ui/walkthrough/WalkthroughOverlay.kt` | Target registry, measurement modifier, overlay, help catalog, contextual dialogs | Copy; adapt styling or host conventions as needed |
| `app/src/main/res/values/walkthrough.xml` | All tour/help copy plus extracted application strings and two gesture labels | Copy/merge resources; check collisions |
| `app/src/main/java/com/bithead/shelter/ui/VaaniApp.kt` | Tour state machine, eligibility, auto-scroll, target anchors, Settings entry points, modal mounting | Selectively merge into destination UI |
| `app/src/main/java/com/bithead/shelter/MainActivity.kt` | Offer initialization, persistence callbacks, session restoration, permissions ordering, recognition suppression | Selectively merge into destination activity/host |
| `app/src/androidTest/java/com/bithead/shelter/ui/walkthrough/WalkthroughTest.kt` | Compose flow and replay instrumentation test | Copy and adapt host arguments/selectors |
| `app/src/androidTest/java/com/bithead/shelter/ui/walkthrough/WalkthroughPreferencesTest.kt` | Preference persistence and resource fallback instrumentation tests | Copy |
| `app/build.gradle.kts` | Instrumentation runner and test dependencies | Merge testing configuration only |
| `docs/walkthrough.md` | Existing implementation notes, bundled shake changes and historical validation | Reference; its named implementation branch is outdated |
| `README.md` | Feature documentation link | Optional documentation merge |

The generated `docs/walkthrough-porting-data.json` contains the full resource catalog, feature definitions, and ordered steps with resolved source copy. XML text values preserve Android string escapes; they are not Android-runtime-rendered strings. The original XML remains the copyable resource source.

## 3. Data model and exact tour order

`FeatureHelp` is an enum with `@StringRes title: Int` and `@StringRes description: Int`. Its IDs are:

`NAVIGATION`, `SAFEWORD`, `ALERT`, `CONTACTS`, `VAULT`, `CAMERA`, `MAP`, `SUPPORT`, `DECOY`, `DISGUISE`, `REPLAY`, `GESTURE`, `LOCATION`, `SECURITY`, `DISPATCH`, `INTEGRITY`, `BLACKOUT`.

`WalkthroughStep(feature: FeatureHelp, tab: Int, itemKey: String)` defines navigation and scrolling. Feature identity is separate from localized display text.

| Step | Feature | Tab index / screen | Lazy-item key | Measured spotlight target |
| --- | --- | --- | --- | --- |
| 1 | NAVIGATION | 0 / Armed | `dashboard_status` | Entire bottom navigation bar; the item key is only a scroll anchor |
| 2 | SAFEWORD | 0 / Armed | `safeword` | Safeword panel |
| 3 | ALERT | 0 / Armed | `alert` | Hold-to-alert control inside the alert panel |
| 4 | CONTACTS | 4 / Settings | `contacts` | Trusted contacts panel |
| 5 | VAULT | 1 / Vault | `vault` | Vault summary panel |
| 6 | CAMERA | 1 / Vault | `media` | Photo/video action row |
| 7 | MAP | 2 / Community | `map` | Map container |
| 8 | SUPPORT | 3 / Support | `support` | Support introduction panel |
| 9 | DECOY | 0 / Armed | `decoy` | Row containing decoy call / preview content |
| 10 | DISGUISE | 4 / Settings | `disguise` | Notes disguise panel |
| 11 | REPLAY | 4 / Settings | `help` | Help & onboarding panel |

The other six catalog entries are accessible through help rather than dedicated tour steps. FeatureHelpDialog renders all 17 entries as a scrolling document; it is not a selectable list with a separate detail route.

## 4. Stored and temporary data

### SharedPreferences

Preference file: `vaani_walkthrough`, `Context.MODE_PRIVATE`.

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `handled_version` | Int | 0 | Last walkthrough version dismissed or finished |
| `completed_version` | Int | 0 when absent | Last walkthrough version finished |

`WalkthroughPreferences.VERSION = 1`.

`shouldOffer = handled_version < VERSION`.

`handle(false)` sets handled_version to VERSION. `handle(true)` also sets completed_version. Skipping after a previous completion preserves completed_version. Writes use SharedPreferences.Editor.apply().

Consequences: Not now is a permanent dismissal for the current version, with replay available in Settings. It is not a reminder for the next launch. Bumping VERSION offers the introduction again. Replaying does not require clearing either preference. No completion timestamp, current step, language, or collected user input is stored here.

### Compose state in VaaniApp

| Variable | Storage | Meaning |
| --- | --- | --- |
| `tourIndex` | rememberSaveable, session-specific key | -2 inactive, -1 introduction, 0 through 10 active step |
| `replay` | rememberSaveable, session-specific key | Whether exit returns to Settings rather than Armed |
| `originIndex`, `originOffset` | rememberSaveable, session-specific keys | Original Settings scroll position for replay exit |
| `showFeatureHelp` | rememberSaveable | Full catalog dialog open state |
| `targetReady` | remember | Target lookup/measurement has completed |
| Five `LazyListState` instances | rememberLazyListState | Scroll state for Armed, Vault, Community, Support, Settings |
| `WalkthroughScope` | remember | Shared list access, active flag, destination index, feature-to-Rect registry |

The host's existing tab and disguise states are also rememberSaveable. The tour's bounds and readiness are recomputed rather than serialized.

### Activity state

`walkthroughPending` starts from preferences.shouldOffer. `walkthroughActive` is a host flag. `permissionsPending` and `foreground` are observable state used to calculate eligibility.

The companion object holds a process-lifetime random UUID `walkthroughSessionId`. Saved-instance state contains `walkthrough_session` and `walkthrough_active`. An activity recreation within that process can restore the active flag and session-keyed Compose state. After process death a new UUID prevents restoring an old tour step; an unhandled first-use offer can restart, while a replay for an already-handled user does not automatically resume.

## 5. Host interface and control flow

VaaniApp receives five walkthrough parameters:

```kotlin
walkthroughSessionId: String = "preview"
walkthroughPending: Boolean
walkthroughEligible: Boolean
onWalkthroughActive: (Boolean) -> Unit
onWalkthroughHandled: (Boolean) -> Unit // true only for Finish
```

MainActivity supplies a real process-session ID. The preview default should not be used as the production session policy.

Normal first-use flow:

1. MainActivity reads preferences, restores same-process activity state, and postpones startup permission requests while the tour is pending/active.
2. VaaniApp computes availability. When pending, available, and inactive, it sets replay=false, locks the vault, tells the host the tour is active, and opens index -1.
3. Start sets tourIndex=0. Back/Next move the index. Next on index 10 finishes with completed=true.
4. Not now, Skip tour, or dialog dismissal finish with completed=false. System Back moves to the previous step above index 0; from step 0 or the introduction it skips.
5. Exit sets index=-2, selects Armed for first use or Settings for replay, releases host suppression, and calls the handled callback.
6. The activity persists the result, clears pending, releases suppression again, and requests any missing startup microphone/location permissions if this was the initial offer.

Replay flow captures the Settings first-visible item and offset, sets replay=true, locks the vault, activates host suppression, and opens the same introduction. On exit the saved Settings scroll is restored by the scrolling effect.

## 6. Eligibility and interruption rules

Activity eligibility is `foreground && !permissionsPending && !showSafewordDialog`.

The UI further requires no emergency recording, sealing, disguise, blackout, camera, generic message dialog, active decoy call, scheduled decoy delay, or media-preview evidence ID.

`visibleTour = tourIndex >= -1 && available`.

Recording, sealing, disguise, blackout, and camera transitions explicitly cancel the active tour index and release its active host flag. They do not persist handled_version. Other availability changes hide the overlay while preserving its current index. In particular, backgrounding hides the tour and permits resuming it on return.

Settings disables replay for emergency recording/sealing. Its button enable state does not reflect all availability conditions: other conditions can make a click return without starting replay. Preserve or improve this distinction when porting.

## 7. Target measurement and scrolling

`CompositionLocalProvider(LocalWalkthrough provides tour)` encloses the scaffold and screens. Each of the five screen LazyColumns uses `LocalWalkthrough.current?.list`, falling back to its own remembered state outside this host. The scope's destination tracks the selected tab.

`Modifier.featureTarget(feature)` uses onGloballyPositioned and boundsInWindow() to register a feature's Rect. Disposal removes it. A target taller than 180dp is represented by only its top 64dp, keeping large cards/maps from producing an oversized spotlight.

On each step, visibility change, or configuration change, a LaunchedEffect clears readiness, changes the tab, and searches that tab's lazy list within 2,000ms. It scrolls through visible item ranges until it finds the stable itemKey, scrolls that item into position, waits for the feature Rect, then marks the target ready.

The step item key and the feature modifier must both be present. They need not be attached to the same composable, as the navigation and alert steps demonstrate. Stable item keys replace fixed numeric item positions, but tab indices are still hard-coded. If the destination branch changes navigation order or replaces LazyColumn, adapt this integration rather than copying numeric indices unchanged.

If targeting fails, the explanation and navigation controls remain usable. The overlay shows a target-unavailable message after 1,800ms without readiness; the search timeout is separately 2,000ms. Next is not disabled while locating a target.

## 8. Overlay rendering and interaction

WalkthroughOverlay is a full-screen Compose Dialog with platform-default width disabled and decorFitsSystemWindows=false. It draws a black shade at alpha .78 around the target, a green 3dp outline, and a triangular pointer. The spotlight rectangle is clipped to the overlay bounds.

The card is centered for the introduction. On steps it sits at the top if the target's center lies below 53% of the window height, otherwise at the bottom. Card width is capped at 520dp, height at 52% of the window; its contents scroll and buttons wrap using FlowRow. Shape corner radius is 20dp; system-bar padding is applied.

The dialog isolates the underlying window; its root also consumes pointer events. The clear spotlight is visual, with no click-through to real safety controls. The scaffold clears underlying semantics while the tour is visible. Titles use heading semantics and step progress uses a polite live region. These are implementation provisions; this analysis does not establish device-level TalkBack, RTL, large-text, or inset correctness.

## 9. Recognition, permissions, and vault integration

MainActivity computes `recognitionSuppressed = walkthroughPending || walkthroughActive`.

The guard is used by gesture callbacks, recognizer readiness, safeword matching, delayed activation, continuous-listening startup/restart, gesture-window startup, onResume, and activateEmergency(). Entering the tour stops the gesture detector and cancels the safeword session. Releasing suppression restarts the appropriate recognition mode in the foreground. Stored listening and gesture mode preferences are retained.

`onLockVault` stops evidence playback, closes media preview, and sets vaultUnlocked=false. The tour invokes this before opening. Vault navigation does not authenticate or unlock the vault. Community suppresses its automatic location refresh and sample-zone warning effect while tourScope.active is true. Map composition can still load its ordinary WebView content and network resources.

When porting, adapt these guards to the destination's actual recognition pipeline, not just its UI. Existing queued callbacks must also honor suppression. Separately preserve permission ordering and the host's cleanup helpers.

One behavior to reconsider: pending alone suppresses recognition even when the tour cannot appear. An existing installation upgraded with Notes disguise enabled can remain in Notes with a pending offer and suppressed voice/shake protection until the safety UI is opened and the offer handled. activateEmergency also shares the suppression guard. Distinguish a deferred offer from a displayed tour if this is undesirable in the destination.

## 10. Content, localization, and contextual help

walkthrough.xml contains 198 strings: 18 tour controls/labels, 34 feature title/body strings, 5 navigation labels, 5 screen titles, 2 gesture labels, and 134 other UI strings.

Only the default English resource file is supplied for this feature. This is a resource-based localization foundation, not shipped multilingual walkthrough translations. There is no walkthrough language picker, translation API, language preference, or voice narration.

FeatureDescription resolves resource IDs at composition time. DISGUISE interpolates ui_personal into `%1$s`, so its header instruction matches the displayed label. tour_progress uses two integer arguments; tour_learn_feature uses a feature title string argument. Preserve those format arguments in translations. Other pre-existing dynamic/hard-coded app text remains outside this extraction.

FeatureHelpLink opens a local AlertDialog containing the shared title and FeatureDescription. Its expanded state uses remember rather than rememberSaveable. The full catalog uses showFeatureHelp and all FeatureHelp enum values.

Contextual placements:

- Armed: DECOY and CAMERA.
- Vault: VAULT.
- Support: SUPPORT.
- Settings: GESTURE, SAFEWORD, SECURITY, LOCATION, CONTACTS, CAMERA, DISGUISE.
- Settings dispatch panel renders FeatureDescription(DISPATCH) inline.
- Global feature help includes all 17, including INTEGRITY and BLACKOUT.

Port the explanations and contextual links you want to retain without replacing the destination's entire screen layout. Copying all of VaaniApp would also copy broad static-label extraction and potentially overwrite that branch's UI work.

## 11. Dependencies and test coverage

The production implementation uses the application's existing Compose UI, Foundation, Material3, activity-compose, Kotlin coroutines, and Android Context/SharedPreferences. It introduces no new production tour dependency or manifest permission.

The bundled test setup adds AndroidJUnitRunner, the existing Compose BOM version 2024.09.00 for androidTest, ui-test-junit4, androidx.test.ext:junit:1.2.1, androidx.test:runner:1.6.2, and debug ui-test-manifest. Match the destination's dependency versions rather than duplicating BOMs. The debug-keystore fallback in the same Gradle diff is unrelated to the tour.

WalkthroughTest mounts VaaniApp with fake callbacks, completes all eleven explanations, performs a long hold at a fixed overlay coordinate on each step, replays, and skips. It checks that counted feature callbacks stay at zero and active suppression is released. It does not use MainActivity or the real recognizer, and does not assert the measured spotlight or scroll restoration coordinates.

WalkthroughPreferencesTest checks first-use eligibility, dismissal/completion persistence, retained completion after skip, nonblank default string fallback for en/hi/ar/fr, and unique feature IDs in the step list. The locale test proves fallback availability, not translated content or rendered RTL layout.

No current test in these files covers activity recreation/process death, background/resume, blocked eligibility, real permission dialogs, physical recognition suppression, all Back/Not now paths, missing targets, TalkBack, or large text. Existing docs describe historical build results; they are not a fresh verification of this checkout. This inventory did not run Gradle or instrumentation.

## 12. Changes to keep separate from a walkthrough-only port

Do not automatically transplant the whole source commit if the goal is only the walkthrough. That commit also contains:

- GestureDetector.kt: gravity filtering and a ShakeTriggerGate requiring three alternating dominant-axis impulses, with timing/cooldown rules.
- ListeningWindow.kt: a monotonic 12-second recognition deadline helper.
- MainActivity.kt: replacement of the older gesture result grace period with the hard deadline; these hunks are separate from the walkthrough suppression guards.
- ShakeTriggerGateTest.kt and ListeningWindowTest.kt; removal of JerkTriggerGateTest.kt.
- gesture_wait / gesture_prompt strings and shake-specific copy updates.
- Broad UI-string extraction and a debug signing fallback.

The walkthrough can work with the destination's existing gesture implementation as long as its start, result, retry, queued activation, and lifecycle paths honor the tour's suppression policy. If retaining the new GESTURE explanation without the new detector, review whether its deliberate-shake wording matches that branch's actual behavior.

## 13. Suggested port sequence

1. Inspect the destination's navigation, screens, speech/gesture pipeline, and existing resource names.
2. Copy the three standalone Kotlin files and merge the required resource strings. Keep preference file/key names if preserving already-handled state is intended.
3. Add the five host parameters and the state machine to the destination app composable. Adapt step tabs, item keys, list states, and featureTarget anchors to its layout.
4. Mount the dialog above the application content and preserve modal/semantics isolation.
5. Add Settings replay and feature help, then desired contextual links. Preserve the destination's UI design and existing warning/permission copy.
6. Add activity preference/session callbacks, availability inputs, permission ordering, recognition suppression and cleanup. Review deferred-offer behavior in Notes.
7. Copy/adapt instrumentation tests and merge their test configuration. Add checks for the lifecycle and eligibility cases relevant to the destination.
8. Validate all steps, first-use dismissal, completion, replay scroll return, Back, rotation, background/resume, missing targets, permission denial, and recognition suppression on a device/emulator when authorized.

The destination branch has not been specified or modified. This map and the JSON export are preparation artifacts, not an applied migration.
