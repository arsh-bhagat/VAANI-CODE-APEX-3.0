# Implementation Plan: Low-Glow Architectural Brutalism (Stitch Remix UI)

## Executive Summary

This plan details the transition of the **VAANI Android application** (`com.bithead.shelter`) from its current legacy dark Material 3 design (green accents, 20dp rounded corners, floating circular buttons) to the **Low-Glow Architectural Brutalism / Dark Bauhaus** design system defined in `stitch_remix_of_shelter_discreet_safety_app`.

---

## 1. Visual & Design System Alignment (`DESIGN.md`)

| Design Parameter | Legacy UI (`app/src/main/...`) | New Stitch Remix UI (`stitch_remix_...`) |
| :--- | :--- | :--- |
| **Aesthetic Philosophy** | Standard dark Material 3 with soft green accents | Neo-Brutalist Bauhaus; high tactical restraint; zero pure-white optical spill |
| **Primary Accent** | Forest Green (`#204637` / `#385E4E`) | Industrial Desaturated Gold/Amber (`#ECC246` / `#C9A227`) |
| **Emergency / Alert** | Standard Red (`#BA1A1A`) | Desaturated Crimson (`#8F2D2D`) |
| **Telemetry / Mesh** | Pale Green / Soft Blue | Slate Steel Blue (`#4A6FA5` / `#3D6B7D`) |
| **Corner Radius** | Rounded pill & 12–20dp cards | Strict 4dp (`RoundedCornerShape(4.dp)`) across panels & buttons |
| **Borders & Dividers** | Soft subtle outlines (`#74747B`) | Precise 1px–1.5px solid strokes (`#2A2A2E` / `#3A3A40`) |
| **Shadows & Elevation** | Elevation tint / ambient blur | Hard, flat 2px solid black neo-shadows (`box-shadow: 2px 2px 0px #000000`) |
| **Typography** | Generic mixed Inter & Space Grotesk | Strict pair: `Space Grotesk` (headings/actions) + `JetBrains Mono` (telemetry/status) |
| **Code Architecture** | Monolithic 865-line `VaaniApp.kt` | Decomposed feature screens (`ui/screens/`) + Reusable Bauhaus components |

---

## 2. Design Tokens & Theme Architecture

### 2.1 Color Palette (`ui/theme/Color.kt`)
Replace legacy green colors with the exact tokens specified in `low_glow_architectural_brutalism/DESIGN.md`:

```kotlin
package com.bithead.shelter.ui.theme

import androidx.compose.ui.graphics.Color

// Canvas & Surfaces (Zero optical spill, dark Bauhaus stepped density)
val CanvasDark = Color(0xFF0D0D0F)           // Base canvas background
val SurfaceLowest = Color(0xFF121214)        // Top bars, sunken inputs
val SurfacePanel = Color(0xFF1A1A1D)         // Default card panel
val SurfaceElevated = Color(0xFF212124)      // Elevated interactive containers
val SurfaceActive = Color(0xFF28282D)        // Pressed / active containers

// Structural Hairline Borders
val BorderSubtle = Color(0xFF2A2A2E)        // 1px standard partition
val BorderMedium = Color(0xFF3A3A40)        // 1.5px active / focus boundary

// Primary Tactical Accents
val GoldPrimary = Color(0xFFECC246)         // Primary armed / active gold
val GoldPrimaryDim = Color(0xFFC9A227)      // Container gold
val GoldOnPrimary = Color(0xFF131315)       // Deep charcoal on gold
val GoldSubtleContainer = Color(0xFF282210) // Gold-tinted pill background

// Emergency & Alert Signals
val CrimsonAlert = Color(0xFF8F2D2D)        // Distress / SOS crimson
val CrimsonContainer = Color(0xFF2A1212)    // SOS-tinted container
val CrimsonText = Color(0xFFFFB4AB)         // High-legibility alert text

// Mesh & Telemetry Accents
val SlateMesh = Color(0xFF4A6FA5)           // Mesh & live relay slate blue
val SlateMeshDim = Color(0xFF3D6B7D)        // Teal/slate secondary container
val SlateMeshSubtle = Color(0xFF122026)     // Slate-tinted pill background

// Typography Tones
val TextBone = Color(0xFFE8E6E0)            // High-legibility off-white (mineral tone)
val TextMidGray = Color(0xFF9A9A9E)         // Secondary metadata & axis labels
val TextMutedCarbon = Color(0xFF5A5A60)     // Terminal brackets & inactive indicators
```

### 2.2 Typography System (`ui/theme/Type.kt`)
The typography pairs **Space Grotesk** (geometric brutalist display with deep ink-traps) with **JetBrains Mono** (hardware telemetry and tracking):

- **Headlines & Titles**: Space Grotesk Bold / SemiBold with negative letter spacing (`-0.02em` to `-0.03em`) and uppercase transformation.
- **Labels & Micro-Tags**: JetBrains Mono Medium with expanded tracking (`0.06em` to `0.08em`), tabular figures, uppercase.
- **Body Content**: Space Grotesk or Inter Regular with clean leading.

### 2.3 Neo-Brutalist Layout Modifiers (`ui/components/BrutalModifiers.kt`)

```kotlin
fun Modifier.neoShadow(
    offset: Dp = 2.dp,
    color: Color = Color.Black
): Modifier = this.drawBehind {
    drawRect(
        color = color,
        topLeft = Offset(offset.toPx(), offset.toPx()),
        size = size
    )
}

fun Modifier.brutalBorder(
    width: Dp = 1.dp,
    color: Color = BorderSubtle
): Modifier = this.border(width, color, RoundedCornerShape(4.dp))
```

---

## 3. Reusable Component Library (`ui/components/`)

1. **`BrutalCard` / `BrutalPanel`**:
   - Background: `SurfacePanel` (`#1A1A1D`)
   - Corner radius: `RoundedCornerShape(4.dp)`
   - Border: 1px `BorderSubtle` (`#2A2A2E`)
   - 2dp solid black neo-shadow offset
   - Structured header partition with category in uppercase `JetBrains Mono` (`labelMedium`, `TextMidGray`).

2. **`BrutalButton`**:
   - **Primary / Armed**: Background `GoldPrimaryDim` (`#C9A227`), border `BorderMedium`, text `GoldOnPrimary` Bold.
   - **Emergency / SOS**: Background `CrimsonAlert` (`#8F2D2D`), border `BorderMedium`, text `TextBone`.
   - **Tactical Neutral**: Background `SurfaceElevated` (`#212124`), border `BorderSubtle`, text `TextBone`.
   - Min height: 48–52dp, corner radius: 4dp, pressed state translation `(1.dp, 1.dp)`.

3. **`StatusPill`**:
   - Height: 22dp, full pill corner radius (`RoundedCornerShape(9999.dp)`), horizontal padding 8dp.
   - 3 variants: **Armed/Active** (gold container), **Critical/SOS** (crimson container), and **Mesh/Relay** (slate container).

4. **`TactileHoldButton`**:
   - Emergency trigger button with continuous progress overlay.
   - Progressive 2-second press with frame updates, haptic feedback pulses, and dynamic label change: `"PRESS & HOLD 2 SECONDS"` ➔ `"HOLD FIRM..."` ➔ `"✓ SOS SENT"`.

5. **`AudioWaveformSimulator` & `TimerDisplay`**:
   - Authentic Bauhaus audio waveform canvas simulator with 13 staggered animated bars (`wave-active` vs `wave-paused`).
   - Digital mono time display (`32sp`, bold, tabular figures, e.g., `17:06`).

6. **`BrutalSwitch` & `HardwareToggle`**:
   - Recessed `#0D0D0F` track with 1px border, 4dp radius. Flat `#212124` block thumb with gold active border.

---

## 4. Screen-by-Screen Implementation Breakdown

### 4.1 Global Application Shell & Navigation (`ui/VaaniApp.kt`)

#### Top App Bar
- **Disguise Action**: Square 40x40dp button with `calculate` icon in `#C9A227` (quick camouflage).
- **Branding Area**: Uppercase mono tag `"VAANI"` in `SlateMesh`, paired with the bold uppercase Screen Title (`"Armed Operations"`, `"Vault"`, etc.).
- **Right Utilities**:
  - **Quick Blackout Button**: 1-tap trigger to enter instant dark duress mode.
  - **Language Selector Chip**: `"EN/हिं"` in JetBrains Mono.
  - **Keyholder Avatar / Status Badge**: Square avatar with 1px border.

#### Bottom Navigation Bar
- 5 Tabs: **Spaces** (`Dashboard`), **Vault**, **Community**, **Support**, **Settings**.
- Styling: Deep charcoal background (`#131315`), top border 1px `#2A2A2E`.
- Active Tab Indicator: 2dp solid gold top border line (`#C9A227`), filled icon, gold bold text. Inactive tabs in `TextMidGray`.

#### Instant Blackout Duress Curtain
- Full-screen pure black `#000000` overlay.
- Low-contrast instructional text (subtle opacity 30%): `"Cloaked Background Recording Active • Tap screen to exit"`.
- Discreet double-tap or triple-tap gesture recognizer to restore view.

---

### 4.2 Tab 0: Armed Operations (`ui/screens/ArmedScreen.kt`)
*(Referencing `stitch_remix_.../armed_operations_sos_panic_ready`)*

1. **Hero Emergency Activation Card**:
   - Prominent square gold block (`56x56dp`) with `crisis_alert` icon.
   - Title: `"MANUALLY ACTIVATE VAANI"`.
   - Full-width `TactileHoldButton` with 2-second hold-to-dispatch animation and haptic pulse.
2. **Safeword Status Card**:
   - Mic icon, green live pulse indicator, current phrase: `"“Weather looks clear today”"`.
   - Direct edit button opening the safeword dialog.
3. **Police & NGO Readiness Banner**:
   - Ready status badge (`verified_user`), 2-column grid showing nearest responders:
     - Sector 14 Police (4 min)
     - Saahas Shelter (600m)
4. **Stacked Quick Action Modules (4 Horizontal Rows)**:
   - **Row 1 - Share Live Location**: SMS to Mom, Kabir with `"Send Link"` action.
   - **Row 2 - Secret Camera**: Photo & silent video burst capture button in crimson `#8F2D2D`.
   - **Row 3 - Fake Incoming Call**: Disguise call with `"Now"`, `"+15s"`, `"+30s"` delay buttons.
   - **Row 4 - Safe Haven**: Nearest safe haven (Apollo 24/7) with direct navigation trigger.
5. **Nearest Outpost Spotlight**:
   - Grayscale architectural thumbnail of nearest location, `"OPEN 24H"` badge, `"HIGH VISIBILITY"`, walk time.
6. **Quick Decoy Gesture Bar**:
   - Bar button for quick blackout trigger.

---

### 4.3 Tab 1: Stealth Evidence Vault (`ui/screens/VaultScreen.kt`)
*(Referencing `stitch_remix_.../vault_primary_home_blackout_dark_bauhaus`)*

1. **Active Recording Sanctuary Card**:
   - Top status header: Red pulsing recording dot, `"RECORDING ACTIVE"` uppercase badge, `"Pause Mic"` / `"Resume Mic"` toggle.
   - Waveform & Timer: Animated audio wave simulator with 13 vertical bars + large tabular timer (`17:06`).
   - Covert Mode Selector (3-column grid):
     - `Audio Loop` (active gold state)
     - `Start Camera` (neutral state, opens CameraX)
     - `Telemetry` (sensor diagnostics)
2. **SMS Live Location Broadcast Card**:
   - Live broadcast radar status badge with animated pulse dot.
   - Latitude/Longitude display: `28.6139° N, 77.2090° E`.
   - Shortlink container: `vaani.safe/v/e8f9024c...` with copy action.
   - Delivery Confirmation Chip: `Delivered (3/3)` with double checkmark.
3. **Tactile Vault Actions**:
   - `"Seal Vault"` button (biometric authentication lock).
   - `"Capture Media"` and `"Import Media"` buttons.
4. **Encrypted Evidence Log**:
   - Incident grouping with chained SHA-256 badges.
   - Evidence Rows: Audio and Photo/Video items with technical tags (`AES-256 Vaulted`, `Zero-Metadata Stripped`, file size, timestamps, lock icons).
   - Audio playback scrubber and decrypted temporary media preview dialog.

---

### 4.4 Tab 2: Community Safe Routes (`ui/screens/CommunityScreen.kt`)
*(Referencing `stitch_remix_.../community_safe_routes_dark_bauhaus`)*

1. **Covert Status & Ambient Indicator**:
   - `"Mesh Escort Active"` with indicator square, paired with `"Encrypted Relay"` gold badge.
2. **High-Contrast Bauhaus Map Viewport**:
   - Framed within 1px solid border with 2dp neo-shadow.
   - Inverted high-contrast dark tile filter via CSS (`leaflet_map.html`).
   - SVG routing lines: thick solid gold line (`#C9A227`, 4px width) for primary safe path; striped dashed crimson path (`#8F2D2D`) for danger corridor.
   - Brutalist square markers for PCR patrol points, unlit hazards, and shelters.
   - Floating Bauhaus map controls (Recenter target reticle, Police radar).
3. **Filter Visualizer Chips**:
   - Bauhaus block tags: `All Signals`, `Well-Lit Streets`, `Patrol Points`, `Safe Haven`, `Risk Corridor`.
4. **Hazard Details Toast & Community Reports**:
   - Dismissible hazard alert cards (e.g., `"Unlit Alleyway & Broken Gate - Flagged by 4 peers"`).
   - Nearby outpost preview cards (Police checkpoints, verified shelters, peer support).

---

### 4.5 Tab 3: Crisis & Legal Support (`ui/screens/SupportScreen.kt`)
*(Referencing `stitch_remix_.../crisis_legal_support_dark_bauhaus`)*

1. **Direct Legal & Crisis Escalation Card**:
   - Header with shield emblem: `"Immediate, confidential intervention networks."`
2. **Rapid Direct Alert 2-Column Grid**:
   - **Nearest Station**: Live ping PCR patrol with `"GPS Ready"` tag.
   - **Alert Verified NGO**: Rapid outreach to regional safe networks.
3. **Multilingual Dialect Switcher**:
   - Horizontal scrolling chips: `English`, `हिन्दी (Hindi)`, `ਪੰਜਾਬੀ (Punjabi)`, `বাংলা (Bengali)`, `मराठी (Marathi)`.
4. **Direct Helpline Cards**:
   - **112**: National Emergency Assistance (Police, Fire, Medical) with direct dialer launch.
   - **15100**: NALSA National Legal Services Authority helpline.
   - **1930**: National Cyber Crime Reporting & Financial Fraud Helpline.
5. **Expandable Rights & Custody Guides**:
   - Accordion cards covering: Legal assistance eligibility, digital privacy rights, forensic evidence integrity.

---

### 4.6 Tab 4: Discreet Settings (`ui/screens/SettingsScreen.kt`)
- **Acoustic Trigger Section**:
  - Safeword display and edit modal.
  - Continuous listening vs. Stealth Mode (Gesture-Wake 12s window) toggle.
- **Hardware Sensitivities**:
  - Accelerometer jerk sensitivity indicator.
- **Trusted Contacts Management**:
  - Add contact fields, native contact picker, remove action, emergency SMS permission status.
- **Decoy App Persona**:
  - `"Disguise as Notes App"` toggle controlling Android launcher component-alias state.
- **Testing Sandbox**:
  - `"Test interface quietly"` button to verify UI states without alerting contacts.

---

### 4.7 Persona Disguise: Notes Cloak (`ui/screens/NotesScreen.kt`)
- Full dark brutalist overhaul of the Notes screen.
- Search bar with hidden passcode triggers (`vaani`, `##`, `911`) to revert to the safety app.
- Grocery checklist with square checkboxes and line-through checked items.
- Personal memo editor with auto-save to private SharedPreferences.

---

## 5. Leaflet Map Overhaul (`app/src/main/assets/leaflet_map.html`)

Update `leaflet_map.html` to align with the Dark Bauhaus design:
1. **Dark Tile Inversion Filter**:
   ```css
   .leaflet-tile-pane {
       filter: brightness(0.65) contrast(1.35) invert(0.9) hue-rotate(180deg) grayscale(20%);
   }
   ```
2. **Bauhaus Square Markers & Tooltips**:
   - Replace standard round Leaflet markers with sharp rectangular SVG pins matching the design system colors (gold for user, crimson for hazards, steel blue for checkpoints).
3. **Geometric Safe Route Polylines**:
   - Render vector polylines using `#C9A227` for safe corridors and dashed `#8F2D2D` for caution areas.

---

## 6. Architectural Structure

```
app/src/main/java/com/bithead/shelter/ui/
├── VaaniApp.kt                   # Root scaffold, top/bottom navigation, overlay management
├── EvidenceCamera.kt             # CameraX photo/video capture
├── theme/
│   ├── Color.kt                  # Stitch Remix Low-Glow palette
│   ├── Type.kt                   # Space Grotesk & JetBrains Mono typography
│   └── Theme.kt                  # MaterialTheme integration
├── components/
│   ├── Components.kt             # Playback state, preview state data classes
│   ├── BrutalComponents.kt       # BrutalCard, BrutalButton, StatusPill, TactileHoldButton
│   └── BrutalWaveform.kt         # AudioWaveformSimulator & timer
└── screens/
    ├── ArmedScreen.kt            # Tab 0: Panic Ready & SOS triggers
    ├── VaultScreen.kt            # Tab 1: Active recording & encrypted evidence log
    ├── CommunityScreen.kt        # Tab 2: Bauhaus map & safe route visualizer
    ├── SupportScreen.kt          # Tab 3: Crisis escalation & legal support
    ├── SettingsScreen.kt         # Tab 4: Discreet preferences & trusted contacts
    └── NotesScreen.kt            # Camouflage Notes screen
```

---

## 7. Phased Implementation Roadmap

- **Phase 1: Theme & Design Tokens Foundation** (`Theme.kt`, `Color.kt`, `Type.kt`, custom modifiers).
- **Phase 2: Core Brutalist UI Components** (`BrutalCard`, `BrutalButton`, `StatusPill`, `TactileHoldButton`, `AudioWaveformSimulator`).
- **Phase 3: Screen Decomposition & Navigation Shell** (Top bar, 5-tab bottom navigation, blackout overlay).
- **Phase 4: Screen Implementations**:
  - 4.1: `ArmedScreen.kt` (Panic Ready & SOS hold trigger).
  - 4.2: `VaultScreen.kt` (Active recording sanctuary & encrypted evidence log).
  - 4.3: `SupportScreen.kt` (Crisis escalation & legal aid).
  - 4.4: `SettingsScreen.kt` & `NotesScreen.kt` (Discreet preferences & Notes cloak).
- **Phase 5: Map Styling & Leaflet Bauhaus Theme** (`leaflet_map.html` dark styling & `CommunityScreen.kt`).
- **Phase 6: End-to-End Verification & Polish** (Interactive testing, inset handling, build verification).
