package com.bithead.shelter.ui.screens

import com.bithead.shelter.ui.localization.uiText

import androidx.compose.material3.Text
import android.location.Location
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.bithead.shelter.ui.walkthrough.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.theme.*
import kotlinx.coroutines.delay

@Composable
internal fun ArmedScreen(emergency: Boolean, isSealing: Boolean, safeword: String, count: Int, listening: Boolean, listeningActive: Boolean, gestureWakeMode: Boolean,
    gestureWindowOpening: Boolean, gestureWindowSecondsRemaining: Int,
    onListen: (Boolean) -> Unit, onEdit: () -> Unit, onTrigger: () -> Unit, navigate: (Int) -> Unit,
    disguise: () -> Unit, info: (String) -> Unit, decoyDelay: Int, scheduleDecoy: (Int) -> Unit,
    trustedContactCount: Int, openCamera: () -> Unit, blackout: () -> Unit, speechAvailable: Boolean,
    threatLabel: String, threatScore: Int, lat: Double?, lng: Double?, activeIncidentId: String?) {
    val context = LocalContext.current
    LazyColumn(state = LocalWalkthrough.current?.lists?.get(0) ?: rememberLazyListState(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "alert") { BrutalPanel(header = "01 / Emergency activation") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).background(GoldPrimaryDim, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CrisisAlert, null, Modifier.size(30.dp), GoldOnPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Heading(if (isSealing) "Sealing evidence" else if (emergency) "Recording active" else "Manually activate VAANI")
                    Caption("Private capture / trusted-contact SMS")
                }
            }
            when {
                isSealing -> Action("Sealing evidence…", Icons.Outlined.Lock, {}, enabled = false)
                emergency -> BrutalButton(onTrigger, Modifier.fillMaxWidth(), tone = BrutalButtonTone.Emergency) { Text(uiText("STOP & SEAL EVIDENCE")) }
                else -> TactileHoldButton(onTrigger, Modifier.featureTarget(FeatureHelp.ALERT))
            }
            if (emergency) {
                HorizontalDivider(color = BorderSubtle)
                ActiveRecordingDetails(threatLabel, threatScore, lat, lng, activeIncidentId)
            }
            Caption(if (trustedContactCount == 0) "No trusted contacts configured. Add them in Settings for emergency SMS." else "$trustedContactCount trusted contacts configured. SMS requires permission and cellular service.")
        } }
        item(key = "safeword") { BrutalPanel(Modifier.featureTarget(FeatureHelp.SAFEWORD), header = "02 / Acoustic trigger") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, tint = GoldPrimary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Heading("Safeword")
                    Caption(if (!speechAvailable) "Voice unavailable — check Voice recognition in Settings" else if (gestureWindowOpening) "Opening microphone…" else if (listeningActive) "Microphone listening" else if (gestureWakeMode) "Monitoring motion" else "Microphone paused")
                }
                BrutalSwitch(listening, onListen, enabled = !emergency && !isSealing, label = "Continuous acoustic trigger")
            }
            Surface(onClick = onEdit, shape = RoundedCornerShape(4.dp), color = SurfaceLowest, border = BorderStroke(1.dp, BorderSubtle)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("“$safeword”", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Outlined.EditNote, uiText("Edit safeword"), tint = GoldPrimary)
                }
            }
            Caption(if (gestureWindowSecondsRemaining > 0) "Listening window: ${gestureWindowSecondsRemaining}s remaining" else if (gestureWakeMode && !listening) "Motion armed / continuous acoustic trigger off" else if (gestureWakeMode) "Motion and continuous acoustic triggers enabled" else if (listening) "Continuous listening while app is foregrounded" else "Acoustic trigger and motion confirmation are off")
        } }
        item { BrutalPanel(header = "03 / Response resources") {
            StatusPill("Public resources", tone = StatusTone.Mesh)
            Heading("Police & support networks")
            Caption("Find nearby public stations and support resources. Responder dispatch is not connected.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Stations", Icons.Outlined.LocalPolice, { openLink(context, "geo:0,0?q=nearby+police+station") }, Modifier.weight(1f), primary = false)
                Action("Support", Icons.Outlined.HealthAndSafety, { navigate(3) }, Modifier.weight(1f), primary = false)
            }
        } }
        item { BrutalPanel(header = "04 / Quick actions") {
            Heading("Live location")
            Caption("Location protection keeps a recent fix ready for emergency SMS to your trusted contacts.")
            Action("Location & contacts", Icons.Outlined.ShareLocation, { navigate(4) }, primary = false)
            HorizontalDivider(color = BorderSubtle)
            Heading("Private camera")
            Caption("Photo or silent video / sealed into the encrypted evidence chain")
            BrutalButton(openCamera, Modifier.fillMaxWidth(), tone = BrutalButtonTone.Emergency) {
                Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text(uiText("CAPTURE MEDIA"))
            }
            FeatureHelpLink(FeatureHelp.CAMERA)
        } }
        item(key = "decoy") { BrutalPanel(header = "05 / Decoy & nearby care") {
            Heading("Fake incoming call")
            FeatureHelpLink(FeatureHelp.DECOY)
            Caption(if (decoyDelay > 0) "Scheduled in ${decoyDelay}s" else "Local incoming-call simulation")
            Row(Modifier.fillMaxWidth().featureTarget(FeatureHelp.DECOY),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30).forEach { seconds ->
                    BrutalButton({ scheduleDecoy(seconds) }, Modifier.weight(1f), tone = BrutalButtonTone.Neutral) {
                        Text(uiText(if (seconds == 0) "Now" else "+${seconds}s"))
                    }
                }
            }
            HorizontalDivider(color = BorderSubtle)
            Heading("Safe haven")
            Caption("Search nearby public hospitals and pharmacies in device maps.")
            Action("Find nearby care", Icons.Outlined.NearMe, { openLink(context, "geo:0,0?q=nearby+hospital") }, primary = false)
        } }
        item { BrutalPanel(header = "06 / Local evidence") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Heading("Stealth vault"); Caption("$count sealed items / on this device") }
                StatusPill("AES-256")
            }
            Action("Open encrypted vault", Icons.Outlined.FolderSpecial, { navigate(1) }, primary = false)
            Action("Community route preview", Icons.Outlined.Map, { navigate(2) }, primary = false)
        } }
        item { Action("Blackout display", Icons.Outlined.VisibilityOff, blackout, primary = false) }
        item { Action("Quick exit to Notes", Icons.Outlined.Calculate, disguise, primary = false) }
    }
}

/** Only composed during active emergency recording; no standby panel is shown. */
@Composable
private fun ActiveRecordingDetails(label: String, score: Int, lat: Double?, lng: Double?, activeIncidentId: String?) {
    var audioTab by rememberSaveable(activeIncidentId) { mutableIntStateOf(0) }
    val captureStartedAt = activeIncidentId?.toLongOrNull()
    var elapsedSeconds by remember(activeIncidentId) { mutableLongStateOf(0L) }
    LaunchedEffect(captureStartedAt) {
        if (captureStartedAt != null) while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - captureStartedAt) / 1_000L).coerceAtLeast(0L)
            delay(250L)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusPill("Recording active", tone = StatusTone.Critical)
        Heading("MIC RECORDING")
        AudioWaveformSimulator(true, Modifier.align(Alignment.CenterHorizontally))
        if (captureStartedAt != null) TimerDisplay(elapsedSeconds, Modifier.align(Alignment.CenterHorizontally))
        Text(uiText("Recording…"), Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall)
        Caption("Auto-seals after 5 minutes")
        TabRow(selectedTabIndex = audioTab, containerColor = ShelterSurfaceRaised) {
            listOf("Audio", "Location").forEachIndexed { index, title ->
                Tab(audioTab == index, onClick = { audioTab = index }, text = { Text(uiText(title)) })
            }
        }
        if (audioTab == 0) ThreatMeter(label, score)
        else Caption(if (lat != null && lng != null) "Last sealed location: $lat, $lng" else "A location fix is requested when evidence is sealed.")
    }
}
