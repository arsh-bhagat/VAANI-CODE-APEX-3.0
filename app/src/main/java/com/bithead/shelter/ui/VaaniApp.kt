package com.bithead.shelter.ui

import android.media.RingtoneManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bithead.shelter.R
import com.bithead.shelter.data.Evidence
import com.bithead.shelter.data.TrustedContact
import com.bithead.shelter.emergency.CachedLocation
import com.bithead.shelter.emergency.ContactAlertStatus
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.components.EvidencePlaybackState
import com.bithead.shelter.ui.components.EvidenceMediaPreviewState
import com.bithead.shelter.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

import com.bithead.shelter.ui.screens.*

private val tabs = listOf("Spaces", "Vault", "Community", "Support", "Settings")
private val tabIcons = listOf(Icons.Outlined.Dashboard, Icons.Outlined.FolderSpecial, Icons.Outlined.NearMe, Icons.AutoMirrored.Outlined.HelpOutline, Icons.Outlined.Tune)
@Composable
fun VaaniApp(
    isEmergency: Boolean, isSealing: Boolean, safeword: String, threatLabel: String, threatScore: Int,
    lat: Double?, lng: Double?, evidence: List<Evidence>, vaultUnlocked: Boolean,
    mapLat: Double?, mapLng: Double?, mapAccuracyMeters: Float?, mapLocationTimestamp: Long?,
    biometricAvailable: Boolean, snackbarHostState: SnackbarHostState,
    onTrigger: () -> Unit, onEditSafeword: () -> Unit,
    playbackState: EvidencePlaybackState, onPlaybackToggle: (Evidence) -> Unit,
    onPlaybackStop: () -> Unit, onPlaybackSeek: (Evidence, Long) -> Unit,
    mediaPreviewState: EvidenceMediaPreviewState, onOpenMedia: (Evidence) -> Unit,
    onCloseMedia: () -> Unit,
    onExport: (Evidence) -> Unit, onDeleteEvidence: (Evidence) -> Unit,
    onVerifyChain: () -> Unit, onUnlockVault: () -> Unit,
    listening: Boolean, listeningActive: Boolean, onListeningChange: (Boolean) -> Unit,
    gestureWakeMode: Boolean, gestureWindowOpening: Boolean, gestureWindowSecondsRemaining: Int,
    onGestureWakeModeChange: (Boolean) -> Unit, onLockVault: () -> Unit, onVaultHidden: () -> Unit,
    disguiseEnabled: Boolean, onDisguiseEnabledChange: (Boolean) -> Unit,
    onRefreshMapLocation: () -> Unit,
    trustedContacts: List<TrustedContact>, smsStatuses: List<ContactAlertStatus>,
    locationTracking: Boolean, cachedLocation: CachedLocation?,
    onAddContact: (String, String) -> Unit, onRemoveContact: (String) -> Unit,
    onPickContact: () -> Unit, onLocationTrackingChange: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit, onImportMedia: () -> Unit,
    pendingEvidenceDir: java.io.File, activeIncidentId: String?, onMediaCaptured: (java.io.File) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var disguised by rememberSaveable { mutableStateOf(disguiseEnabled) }
    var blackout by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var decoyDelay by rememberSaveable { mutableIntStateOf(0) }
    var decoyCall by rememberSaveable { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(disguiseEnabled) { disguised = disguiseEnabled }
    LaunchedEffect(tab, disguised, blackout, vaultUnlocked) {
        if (tab != 1 || disguised || blackout || !vaultUnlocked) onVaultHidden()
    }
    LaunchedEffect(decoyDelay) {
        if (decoyDelay > 0) {
            delay(decoyDelay * 1000L)
            decoyDelay = 0
            decoyCall = true
        }
    }
    val disguise = { onLockVault(); disguised = true }
    val unlockDisguise = { disguised = false }
    BackHandler(blackout || disguised || tab != 0) {
        when { blackout -> blackout = false; disguised -> Unit; else -> tab = 0 }
    }
    if (blackout) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { blackout = false })
        }.semantics {
            customActions = listOf(CustomAccessibilityAction("Exit blackout") { blackout = false; true })
        }) {
            Text("Double-tap to exit blackout", color = TextMidGray.copy(alpha = 0.3f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
        }
        return
    }
    if (showCamera && !disguised) {
        EvidenceCamera(
            pendingDir = pendingEvidenceDir,
            incidentId = activeIncidentId,
            onPhoto = { showCamera = false; onMediaCaptured(it) },
            onVideo = { showCamera = false; onMediaCaptured(it) },
            onClose = { showCamera = false }
        )
        return
    }
    Scaffold(
        containerColor = ShelterInk,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (disguised) {
                    Column(
                        Modifier.weight(1f).pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                if (withTimeoutOrNull(2_000L) { tryAwaitRelease() } == null) unlockDisguise()
                            })
                        }
                    ) {
                        Text("PERSONAL", style = MaterialTheme.typography.labelMedium, color = ShelterTextDim)
                        Text("All Notes", style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    IconButton(onClick = disguise, modifier = Modifier.brutalBorder().background(SurfaceElevated)) {
                        Icon(Icons.Outlined.Calculate, "Show notes disguise", tint = GoldPrimaryDim)
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("VAANI", style = MaterialTheme.typography.labelMedium, color = ShelterBlueSoft)
                        Text(listOf("Armed Operations", "Evidence / Vault", "Community Routes", "Support Network", "Discreet Settings")[tab].uppercase(Locale.ROOT), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { onLockVault(); blackout = true }) {
                        Icon(Icons.Outlined.VisibilityOff, "Blackout display", tint = TextMidGray)
                    }
                    Image(painterResource(R.drawable.vaani_mark), "VAANI", Modifier.size(40.dp).brutalBorder().clip(RoundedCornerShape(4.dp)))
                }
            }
        },
        bottomBar = {
            if (!disguised) Column(Modifier.background(SurfaceLowest).navigationBarsPadding()) {
                HorizontalDivider(color = BorderSubtle)
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    tabs.forEachIndexed { i, title ->
                        val active = tab == i
                        Column(Modifier.weight(1f).selectable(selected = active, role = androidx.compose.ui.semantics.Role.Tab,
                            onClick = { tab = i }), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) GoldPrimaryDim else Color.Transparent))
                            Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(tabIcons[i], null, tint = if (active) GoldPrimary else TextMidGray)
                                Text(title, color = if (active) GoldPrimary else TextMidGray,
                                    fontSize = 9.sp, maxLines = 1, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (disguised) NotesScreen(unlockDisguise)
            else when (tab) {
                0 -> ArmedScreen(isEmergency, isSealing, safeword, evidence.size, listening, listeningActive, gestureWakeMode,
                    gestureWindowOpening, gestureWindowSecondsRemaining, onListeningChange, onEditSafeword, onTrigger,
                    { tab = it }, disguise, { message = it }, decoyDelay, { if (it == 0) decoyCall = true else decoyDelay = it }, trustedContacts.size,
                    { showCamera = true }, { onLockVault(); blackout = true })
                1 -> VaultScreen(isEmergency, isSealing, threatLabel, threatScore, lat, lng, evidence, vaultUnlocked, biometricAvailable,
                    onTrigger, onUnlockVault, onVerifyChain, playbackState, onPlaybackToggle, onPlaybackStop,
                    onPlaybackSeek, mediaPreviewState, onOpenMedia, onCloseMedia, onExport, onDeleteEvidence,
                    { onLockVault(); blackout = true }, { showCamera = true }, onImportMedia,
                    onLockVault, locationTracking, cachedLocation, smsStatuses, activeIncidentId)
                2 -> CommunityScreen(mapLat, mapLng, mapAccuracyMeters, mapLocationTimestamp, onRefreshMapLocation) { message = it }
                3 -> SupportScreen(disguise)
                4 -> SettingsScreen(safeword, listening, onListeningChange, gestureWakeMode, onGestureWakeModeChange, onEditSafeword, disguiseEnabled, onDisguiseEnabledChange, disguise, { message = it }, trustedContacts, smsStatuses, locationTracking, cachedLocation, onAddContact, onRemoveContact, onPickContact, onLocationTrackingChange, onRequestSmsPermission, onImportMedia, { showCamera = true })
            }
        }
    }
    message?.let { text ->
        AlertDialog(onDismissRequest = { message = null }, title = { Text("VAANI") }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Got it") } })
    }
    if (decoyCall) {
        val context = LocalContext.current
        DisposableEffect(Unit) {
            val ringtone = runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                RingtoneManager.getRingtone(context, uri).also { it.play() }
            }.getOrNull()
            onDispose { ringtone?.stop() }
        }
        AlertDialog(onDismissRequest = { decoyCall = false }, icon = { Icon(Icons.Outlined.Phone, null) },
            title = { Text("Incoming call · Home") }, text = { Text("Local decoy call. It does not place a real call or play a voice clip.") },
            confirmButton = { TextButton(onClick = { decoyCall = false }) { Text("End call") } })
    }
    if (mediaPreviewState.evidenceId != null) MediaPreview(mediaPreviewState, onCloseMedia)
}
