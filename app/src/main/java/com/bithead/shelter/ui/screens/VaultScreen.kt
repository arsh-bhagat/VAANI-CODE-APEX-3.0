package com.bithead.shelter.ui.screens

import com.bithead.shelter.ui.localization.uiText
import com.bithead.shelter.ui.localization.tr

import androidx.compose.material3.Text
import android.graphics.BitmapFactory
import android.location.Location
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.bithead.shelter.ai.IncidentSummary
import com.bithead.shelter.data.Evidence
import com.bithead.shelter.emergency.CachedLocation
import com.bithead.shelter.emergency.ContactAlertStatus
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.components.EvidencePlaybackState
import com.bithead.shelter.ui.components.EvidenceMediaPreviewState
import com.bithead.shelter.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun VaultScreen(emergency: Boolean, isSealing: Boolean, evidence: List<Evidence>, unlocked: Boolean,
    biometric: Boolean, trigger: () -> Unit, unlock: () -> Unit, verify: () -> Unit,
    playback: EvidencePlaybackState, togglePlayback: (Evidence) -> Unit, stopPlayback: () -> Unit,
    seekPlayback: (Evidence, Long) -> Unit, mediaPreview: EvidenceMediaPreviewState,
    openMedia: (Evidence) -> Unit, closeMedia: () -> Unit, export: (Evidence) -> Unit,
    deleteEvidence: (Evidence) -> Unit, blackout: () -> Unit, openCamera: () -> Unit,
    importMedia: () -> Unit, lock: () -> Unit, locationTracking: Boolean,
    cachedLocation: CachedLocation?, smsStatuses: List<ContactAlertStatus>) {
    val uiLanguage = com.bithead.shelter.ui.localization.LocalUiLanguage.current
    val dateFormat = remember(uiLanguage) { SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag(uiLanguage)) }
    var pendingDeletion by remember { mutableStateOf<Evidence?>(null) }
    val incidents = remember(evidence) { evidence.groupBy { it.incidentId ?: "legacy-${it.id}" }.entries.toList() }
    LazyColumn(state = LocalWalkthrough.current?.lists?.get(1) ?: rememberLazyListState(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "vault") { Panel(Modifier.featureTarget(FeatureHelp.VAULT)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, tint = ShelterSafe); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Heading("Stealth Evidence Vault"); Caption("On-device • AES-256-GCM") }; Badge(if (unlocked) "Unlocked" else "Locked") } } }
        item { BrutalPanel(header = "01 / Emergency location SMS") {
            StatusPill(if (locationTracking) "Location protection on" else "Location protection off", tone = StatusTone.Mesh)
            Text(uiText(cachedLocation?.let { String.format(Locale.ROOT, "%.5f°, %.5f°", it.latitude, it.longitude) }
                ?: "WAITING FOR LOCATION"), style = MaterialTheme.typography.labelLarge)
            cachedLocation?.let { Caption("Last fix: ${dateFormat.format(Date(it.timestampMillis))}") }
            Caption("Emergency SMS includes a maps link when a location is available.")
            Caption("After recording is sealed, the original trusted contacts receive its encrypted-file SHA-256 hash in a follow-up SMS.")
            if (smsStatuses.isEmpty()) Caption("No SMS delivery receipts yet")
            else smsStatuses.forEach { status ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(status.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    StatusPill(status.status, tone = if (status.status.startsWith("Send failed")) StatusTone.Critical else StatusTone.Mesh)
                }
            }
        } }
        item { Action("Blackout display", Icons.Outlined.VisibilityOff, blackout, primary = false) }
        item(key = "media") { Row(Modifier.fillMaxWidth().featureTarget(FeatureHelp.CAMERA), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Action("Capture", Icons.Outlined.PhotoCamera, openCamera, Modifier.weight(1f))
            Action("Import", Icons.Outlined.FileOpen, importMedia, Modifier.weight(1f), primary = false)
        } }
        if (unlocked) item { Action("Lock vault", Icons.Outlined.Lock, lock, primary = false) }
        if (emergency) item { Action("Stop & seal vault", Icons.Outlined.Lock, trigger) }
        if (isSealing) item { Action("Sealing evidence…", Icons.Outlined.Lock, {}, enabled = false) }
        item { Panel { Heading("Offline first storage"); FeatureHelpLink(FeatureHelp.VAULT); Caption("Sealed recordings stay on this device. Unlock to play or export a chain-of-custody record. No cloud or peer sync is connected.") } }
        item { Heading("ENCRYPTED EVIDENCE LOG") }
        if (!unlocked) item { Panel(color = ShelterSurfaceRaised) {
            Icon(Icons.Outlined.Fingerprint, null, Modifier.size(36.dp), ShelterSafe)
            Heading("Your evidence is private")
            Caption(if (biometric) "Authenticate to view sealed recordings." else "Set up a device screen lock or biometrics to access the vault.")
            Button(onClick = unlock, enabled = biometric, modifier = Modifier.fillMaxWidth()) { Text(uiText("Unlock vault")) }
        } } else {
            item { Action("Verify chain integrity", Icons.Outlined.VerifiedUser, verify) }
            if (evidence.isEmpty()) item { Panel { Heading("No sealed recordings yet"); Caption("Record from Vaani, then stop and seal to create your first entry.") } }
            items(incidents, key = { it.key }) { incident ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(uiText("INCIDENT ${incident.key.takeLast(8)}"), style = MaterialTheme.typography.labelLarge, color = ShelterSafe)
                            Caption("${incident.value.size} sealed item${if (incident.value.size == 1) "" else "s"}")
                        }
                        Badge("Hash chained")
                    }
                    incident.value.forEach { entry -> EvidenceCard(
                    index = entry.id,
                    timestamp = dateFormat.format(Date(entry.createdAt)),
                    threatLabel = entry.threatLabel,
                    threatScore = entry.threatScore,
                    lat = entry.latitude,
                    lng = entry.longitude,
                    chainHash = entry.sha256,
                    previousHash = entry.previousHash,
                    summary = IncidentSummary.describeForUi(entry, uiLanguage),
                    mediaType = entry.mediaType,
                    playback = playback,
                    onPlayPause = { togglePlayback(entry) },
                    onStop = stopPlayback,
                    onSeek = { seekPlayback(entry, it) },
                    onOpenMedia = { openMedia(entry) },
                    onExport = { export(entry) },
                    deletionPending = entry.deletedFileHash != null,
                    onDelete = { pendingDeletion = entry }
                    ) }
                }
            }
        }
    }
    pendingDeletion?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(uiText(if (entry.deletedFileHash == null) "Delete evidence?" else "Retry deletion?")) },
            text = { Text(uiText("The encrypted ${entry.mediaType.lowercase()} file will be permanently removed from this device. Its hash-only deletion record stays in the chain, so later evidence can still be verified.")) },
            confirmButton = {
                TextButton(onClick = { pendingDeletion = null; deleteEvidence(entry) }) {
                    Text(uiText("Delete permanently"), color = ShelterDanger)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text(uiText("Cancel")) } }
        )
    }
}

@Composable
internal fun MediaPreview(state: EvidenceMediaPreviewState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ShelterSurface,
            shape = RoundedCornerShape(4.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Heading(if (state.mediaType.equals("VIDEO", true)) "Video evidence" else "Image evidence")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, uiText("Close preview")) }
                }
                when {
                    state.isPreparing -> Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.filePath == null -> Caption("Preview unavailable")
                    state.mediaType.equals("VIDEO", true) -> AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                val controller = MediaController(context)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoPath(state.filePath)
                                setOnPreparedListener { start() }
                            }
                        },
                        update = { view -> if (!view.isPlaying) view.start() },
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                    )
                    else -> {
                        val bitmap = remember(state.filePath) { BitmapFactory.decodeFile(state.filePath)?.asImageBitmap() }
                        if (bitmap == null) Caption("This image could not be decoded") else Image(
                            bitmap = bitmap,
                            contentDescription = tr("Decrypted evidence image"),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                        )
                    }
                }
                Caption("Temporary decrypted preview • removed when this window closes")
            }
        }
    }
}
