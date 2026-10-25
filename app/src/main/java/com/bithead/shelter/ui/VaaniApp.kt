package com.bithead.shelter.ui

import android.media.RingtoneManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bithead.shelter.ui.walkthrough.*
import kotlinx.coroutines.flow.first
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
    walkthroughSessionId: String = "preview",
    walkthroughPending: Boolean, walkthroughEligible: Boolean,
    onWalkthroughActive: (Boolean) -> Unit, onWalkthroughHandled: (Boolean) -> Unit,
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
    val listStates = listOf(rememberLazyListState(), rememberLazyListState(), rememberLazyListState(), rememberLazyListState(), rememberLazyListState())
    val tour = remember { WalkthroughScope(listStates) }
    var tourIndex by rememberSaveable(key = "tour_index_$walkthroughSessionId") { mutableIntStateOf(-2) }
    var replay by rememberSaveable(key = "tour_replay_$walkthroughSessionId") { mutableStateOf(false) }
    var originIndex by rememberSaveable(key = "tour_origin_index_$walkthroughSessionId") { mutableIntStateOf(0) }
    var originOffset by rememberSaveable(key = "tour_origin_offset_$walkthroughSessionId") { mutableIntStateOf(0) }
    var showFeatureHelp by rememberSaveable { mutableStateOf(false) }
    var targetReady by remember { mutableStateOf(false) }
    val accountPrefs = LocalContext.current.getSharedPreferences("vaani_account_setup", android.content.Context.MODE_PRIVATE)
    var accountSetupDone by remember { mutableStateOf(accountPrefs.getBoolean("done", false)) }
    val accountSetupVisible = !accountSetupDone && walkthroughEligible && !isEmergency && !isSealing && !disguised && !blackout
    val available = accountSetupDone && walkthroughEligible && !isEmergency && !isSealing && !disguised && !blackout &&
        !showCamera && message == null && !decoyCall && decoyDelay == 0 && mediaPreviewState.evidenceId == null
    val visibleTour = tourIndex >= -1 && available
    SideEffect { tour.active = tourIndex >= -1 }
    val configuration = LocalConfiguration.current
    val finishTour: (Boolean) -> Unit = { completed ->
        tourIndex = -2
        tab = if (replay) 4 else 0
        onWalkthroughActive(false)
        onWalkthroughHandled(completed)
    }
    val startReplay = {
        if (available) {
            originIndex = listStates[4].firstVisibleItemIndex
            originOffset = listStates[4].firstVisibleItemScrollOffset
            replay = true
            onLockVault()
            onWalkthroughActive(true)
            tourIndex = -1
        }
    }
    LaunchedEffect(walkthroughPending, available) {
        if (walkthroughPending && available && tourIndex == -2) {
            replay = false
            onLockVault()
            onWalkthroughActive(true)
            tourIndex = -1
        }
    }
    LaunchedEffect(tourIndex, visibleTour, configuration) {
        targetReady = false
        if (visibleTour && tourIndex >= 0) {
            val step = walkthroughSteps[tourIndex]
            tab = step.tab
            withTimeoutOrNull(2_000L) {
                val state = listStates[step.tab]
                snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > 0 }
                // Search stable item keys rather than coupling the tour to screen item counts.
                var index = 0
                while (index < state.layoutInfo.totalItemsCount) {
                    state.scrollToItem(index)
                    val visible = snapshotFlow { state.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
                    val found = visible.firstOrNull { it.key == step.itemKey }
                    if (found != null) {
                        state.scrollToItem(found.index)
                        snapshotFlow { tour.targets[step.feature] }.first { it != null }
                        targetReady = true
                        break
                    }
                    index = maxOf(index + 1, visible.maxOf { it.index } + 1)
                }
            }
        } else if (tourIndex == -2 && replay) {
            listStates[4].scrollToItem(originIndex, originOffset)
            replay = false
        }
    }
    LaunchedEffect(isEmergency, isSealing, disguised, blackout, showCamera) {
        if (tourIndex >= -1 && (isEmergency || isSealing || disguised || blackout || showCamera)) {
            tourIndex = -2
            onWalkthroughActive(false)
        }
    }
    DisposableEffect(Unit) { onDispose { onWalkthroughActive(false) } }
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
    val unlockDisguise = { tab = 0; disguised = false }
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
    CompositionLocalProvider(LocalWalkthrough provides tour) {
    Scaffold(
        modifier = if (visibleTour) Modifier.clearAndSetSemantics { } else Modifier,
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
            if (!disguised) Column(Modifier.featureTarget(FeatureHelp.NAVIGATION).background(SurfaceLowest).navigationBarsPadding()) {
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
            if (disguised) NotesScreen(safeword, unlockDisguise)
            else if (accountSetupVisible) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    GoogleAccountPanel {
                        accountPrefs.edit().putBoolean("done", true).apply()
                        accountSetupDone = true
                    }
                }
            }
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
                4 -> SettingsScreen(safeword, listening, onListeningChange, gestureWakeMode, onGestureWakeModeChange, onEditSafeword, disguiseEnabled, onDisguiseEnabledChange, disguise, { message = it }, trustedContacts, smsStatuses, locationTracking, cachedLocation, onAddContact, onRemoveContact, onPickContact, onLocationTrackingChange, onRequestSmsPermission, onImportMedia, { showCamera = true }, startReplay, { showFeatureHelp = true }, available)
            }
        }
    }
    } // CompositionLocalProvider
    if (showFeatureHelp && available) FeatureHelpDialog { showFeatureHelp = false }
    if (visibleTour) {
        WalkthroughOverlay(tourIndex, if (tourIndex >= 0) tour.targets[walkthroughSteps[tourIndex].feature] else null,
            targetReady && (tourIndex < 0 || tour.targets[walkthroughSteps[tourIndex].feature] != null), onStart = { targetReady = false; tourIndex = 0 }, onBack = { targetReady = false; tourIndex-- },
            onNext = { targetReady = false; if (tourIndex == walkthroughSteps.lastIndex) finishTour(true) else tourIndex++ },
            onSkip = { finishTour(false) })
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
