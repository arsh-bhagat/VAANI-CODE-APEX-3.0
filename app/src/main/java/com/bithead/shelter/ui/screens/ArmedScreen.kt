package com.bithead.shelter.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.theme.*

@Composable
internal fun ArmedScreen(emergency: Boolean, isSealing: Boolean, safeword: String, count: Int, listening: Boolean, listeningActive: Boolean, gestureWakeMode: Boolean,
    gestureWindowOpening: Boolean, gestureWindowSecondsRemaining: Int,
    onListen: (Boolean) -> Unit, onEdit: () -> Unit, onTrigger: () -> Unit, navigate: (Int) -> Unit,
    disguise: () -> Unit, info: (String) -> Unit, decoyDelay: Int, scheduleDecoy: (Int) -> Unit,
    trustedContactCount: Int, openCamera: () -> Unit, blackout: () -> Unit) {
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
                emergency -> BrutalButton(onTrigger, Modifier.fillMaxWidth(), tone = BrutalButtonTone.Emergency) { Text("STOP & SEAL EVIDENCE") }
                else -> TactileHoldButton(onTrigger, Modifier.featureTarget(FeatureHelp.ALERT))
            }
            Caption(if (trustedContactCount == 0) "No trusted contacts configured. Add them in Settings for emergency SMS." else "$trustedContactCount trusted contacts configured. SMS requires permission and cellular service.")
        } }
        item(key = "safeword") { BrutalPanel(Modifier.featureTarget(FeatureHelp.SAFEWORD), header = "02 / Acoustic trigger") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, tint = GoldPrimary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Heading("Safeword")
                    Caption(if (gestureWindowOpening) "Opening microphone…" else if (listeningActive) "Microphone listening" else if (listening && gestureWakeMode) "Waiting for gesture" else "Microphone paused")
                }
                BrutalSwitch(listening, onListen, enabled = !emergency && !isSealing, label = "Safeword protection")
            }
            Surface(onClick = onEdit, shape = RoundedCornerShape(4.dp), color = SurfaceLowest, border = BorderStroke(1.dp, BorderSubtle)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("“$safeword”", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Outlined.EditNote, "Edit safeword", tint = GoldPrimary)
                }
            }
            Caption(if (gestureWakeMode && listeningActive) "Listening window: ${gestureWindowSecondsRemaining}s remaining" else if (gestureWakeMode) "Gesture wake / 12-second listening window" else "Continuous listening while app is foregrounded")
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
                Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("CAPTURE MEDIA")
            }
            FeatureHelpLink(FeatureHelp.CAMERA)
        } }
        item(key = "decoy") { BrutalPanel(Modifier.featureTarget(FeatureHelp.DECOY), header = "05 / Decoy & nearby care") {
            Heading("Fake incoming call")
            FeatureHelpLink(FeatureHelp.DECOY)
            Caption(if (decoyDelay > 0) "Scheduled in ${decoyDelay}s" else "Local incoming-call simulation")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30).forEach { seconds ->
                    BrutalButton({ scheduleDecoy(seconds) }, Modifier.weight(1f), tone = BrutalButtonTone.Neutral) {
                        Text(if (seconds == 0) "Now" else "+${seconds}s")
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
