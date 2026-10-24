package com.bithead.shelter.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Location
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bithead.shelter.ui.walkthrough.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.bithead.shelter.R
import com.bithead.shelter.ai.IncidentSummary
import com.bithead.shelter.data.Evidence
import com.bithead.shelter.data.TrustedContact
import com.bithead.shelter.emergency.CachedLocation
import com.bithead.shelter.emergency.ContactAlertStatus
import com.bithead.shelter.ui.components.EvidenceCard
import com.bithead.shelter.ui.components.EvidencePlaybackState
import com.bithead.shelter.ui.components.EvidenceMediaPreviewState
import com.bithead.shelter.ui.components.ListeningBars
import com.bithead.shelter.ui.components.ThreatMeter
import com.bithead.shelter.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

private val tabLabels = listOf(R.string.nav_armed, R.string.nav_vault, R.string.nav_community, R.string.nav_support, R.string.nav_settings)
private val screenLabels = listOf(R.string.screen_armed, R.string.screen_vault, R.string.screen_community, R.string.screen_support, R.string.screen_settings)
private val tabIcons = listOf(Icons.Outlined.Dashboard, Icons.Outlined.FolderSpecial, Icons.Outlined.NearMe, Icons.AutoMirrored.Outlined.HelpOutline, Icons.Outlined.Tune)
private val disguisePasscodes = setOf("##", "vaani", "911").also {
    check(it.size == 3 && it.all(String::isNotBlank))
}

internal data class MapDangerZone(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val level: String,
    val title: String,
    val detail: String
)

internal val sampleDangerZones = listOf(
    MapDangerZone(28.6421, 77.2194, 340.0, "high", "Harassment reports", "8 sample reports in the past 30 days"),
    MapDangerZone(28.6228, 77.2087, 270.0, "medium", "Poorly lit stretch", "5 sample reports in the past 30 days"),
    MapDangerZone(28.6356, 77.2315, 230.0, "medium", "Isolated route", "3 sample reports in the past 30 days")
)

internal fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val result = FloatArray(1)
    Location.distanceBetween(lat1, lng1, lat2, lng2, result)
    return result[0].toDouble()
}

private fun adaptiveDangerZones(latitude: Double?, longitude: Double?): List<MapDangerZone> {
    if (latitude == null || longitude == null ||
        !latitude.isFinite() || !longitude.isFinite() ||
        distanceMeters(latitude, longitude, 28.6335, 77.2199) <= 50_000.0) {
        return sampleDangerZones
    }

    fun shifted(northMeters: Double, eastMeters: Double, radius: Double, level: String, title: String, detail: String): MapDangerZone {
        val lat = (latitude + northMeters / 111_320.0).coerceIn(-89.999, 89.999)
        val longitudeScale = (111_320.0 * abs(cos(Math.toRadians(latitude)))).coerceAtLeast(1.0)
        val rawLongitude = longitude + eastMeters / longitudeScale
        val lng = ((rawLongitude + 540.0) % 360.0) - 180.0
        return MapDangerZone(lat, lng, radius, level, title, detail)
    }

    return listOf(
        shifted(120.0, 160.0, 340.0, "high", "Nearby incident cluster", "8 adaptive sample reports in this map area"),
        shifted(-420.0, -250.0, 270.0, "medium", "Poorly lit stretch", "5 adaptive sample reports in this map area"),
        shifted(480.0, -380.0, 230.0, "medium", "Isolated route", "3 adaptive sample reports in this map area")
    )
}

private fun dangerZonesJson(zones: List<MapDangerZone>) = JSONArray().apply {
    check(zones.all { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && it.radiusMeters > 0 })
    zones.forEach { zone ->
        put(JSONObject()
            .put("lat", zone.latitude)
            .put("lng", zone.longitude)
            .put("radius", zone.radiusMeters)
            .put("level", zone.level)
            .put("title", zone.title)
            .put("detail", zone.detail))
    }
}.toString()

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
    val available = walkthroughEligible && !isEmergency && !isSealing && !disguised && !blackout &&
        !showCamera && message == null && !decoyCall && decoyDelay == 0 && mediaPreviewState.evidenceId == null
    val visibleTour = tourIndex >= -1 && available
    tour.active = tourIndex >= -1
    tour.destination = tab
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
    val unlockDisguise = { disguised = false }
    BackHandler(blackout || disguised || tab != 0) {
        when { blackout -> blackout = false; disguised -> Unit; else -> tab = 0 }
    }
    if (blackout) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            TextButton(onClick = { blackout = false }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                Text(stringResource(R.string.ui_return_to_vault), color = Color.Gray)
            }
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
                        Text(stringResource(R.string.ui_personal), style = MaterialTheme.typography.labelMedium, color = ShelterTextDim)
                        Text(stringResource(R.string.ui_all_notes), style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    IconButton(onClick = disguise) { Icon(Icons.Outlined.Calculate, "Show notes disguise") }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(stringResource(R.string.ui_vaani), style = MaterialTheme.typography.labelMedium, color = ShelterTextDim)
                        Text(stringResource(screenLabels[tab]), style = MaterialTheme.typography.titleLarge)
                    }
                    Image(painterResource(R.drawable.vaani_mark), "VAANI", Modifier.size(44.dp).clip(CircleShape))
                }
            }
        },
        bottomBar = {
            if (!disguised) NavigationBar(modifier = Modifier.featureTarget(FeatureHelp.NAVIGATION), containerColor = ShelterInk, tonalElevation = 0.dp) {
                tabLabels.forEachIndexed { i, label ->
                    val title = stringResource(label)
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(tabIcons[i], title) },
                        label = { Text(title, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = ShelterSafeSoft, selectedIconColor = ShelterSafe, selectedTextColor = ShelterSafe))
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (disguised) NotesScreen(unlockDisguise)
            else when (tab) {
                0 -> Dashboard(isEmergency, isSealing, safeword, evidence.size, listening, listeningActive, gestureWakeMode,
                    gestureWindowOpening, gestureWindowSecondsRemaining, onListeningChange, onEditSafeword, onTrigger,
                    { tab = it }, disguise, { message = it }, decoyDelay, { if (it == 0) decoyCall = true else decoyDelay = it }, trustedContacts.size,
                    { showCamera = true })
                1 -> Vault(isEmergency, isSealing, threatLabel, threatScore, lat, lng, evidence, vaultUnlocked, biometricAvailable,
                    onTrigger, onUnlockVault, onVerifyChain, playbackState, onPlaybackToggle, onPlaybackStop,
                    onPlaybackSeek, mediaPreviewState, onOpenMedia, onCloseMedia, onExport, onDeleteEvidence,
                    { blackout = true }, { showCamera = true }, onImportMedia)
                2 -> Community(mapLat, mapLng, mapAccuracyMeters, mapLocationTimestamp, onRefreshMapLocation) { message = it }
                3 -> Support(disguise)
                4 -> Settings(safeword, listening, onListeningChange, gestureWakeMode, onGestureWakeModeChange, onEditSafeword, disguiseEnabled, onDisguiseEnabledChange, disguise, { message = it }, trustedContacts, smsStatuses, locationTracking, cachedLocation, onAddContact, onRemoveContact, onPickContact, onLocationTrackingChange, onRequestSmsPermission, onImportMedia, { showCamera = true }, startReplay, { showFeatureHelp = true }, !isEmergency && !isSealing)
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
        AlertDialog(onDismissRequest = { message = null }, title = { Text(stringResource(R.string.ui_vaani)) }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ui_got_it)) } })
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
            title = { Text(stringResource(R.string.ui_incoming_call_home)) }, text = { Text(stringResource(R.string.ui_local_decoy_call_it_does_not_place_a_real_call_or_play_a_voice_cl)) },
            confirmButton = { TextButton(onClick = { decoyCall = false }) { Text(stringResource(R.string.ui_end_call)) } })
    }
    if (mediaPreviewState.evidenceId != null) MediaPreview(mediaPreviewState, onCloseMedia)
}

@Composable
private fun Panel(modifier: Modifier = Modifier, color: Color = ShelterSurface, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun PreviewActionPanel(title: String, detail: String, icon: ImageVector, onOpen: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ShelterSafe)
            Spacer(Modifier.width(10.dp))
            Heading(title)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(9.dp).clip(CircleShape).background(ShelterAmber))
        }
        Caption(detail)
        Action(stringResource(R.string.ui_open_preview), Icons.Outlined.OpenInNew, onOpen, primary = false)
        Text(stringResource(R.string.ui_preview_no_alert_was_sent), color = ShelterAmber, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Heading(text: String) { Text(text, style = MaterialTheme.typography.titleMedium) }

@Composable
private fun Caption(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = ShelterTextDim) }

@Composable
private fun Badge(text: String) {
    Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(ShelterSafeSoft).padding(horizontal = 9.dp, vertical = 5.dp),
        color = ShelterSafe, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun Action(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) ShelterSafe else ShelterSurfaceRaised,
            contentColor = if (primary) Color.White else MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(text)
    }
}

@Composable
private fun Dashboard(emergency: Boolean, isSealing: Boolean, safeword: String, count: Int, listening: Boolean, listeningActive: Boolean, gestureWakeMode: Boolean,
    gestureWindowOpening: Boolean, gestureWindowSecondsRemaining: Int,
    onListen: (Boolean) -> Unit, onEdit: () -> Unit, onTrigger: () -> Unit, navigate: (Int) -> Unit,
    disguise: () -> Unit, info: (String) -> Unit, decoyDelay: Int, scheduleDecoy: (Int) -> Unit,
    trustedContactCount: Int, openCamera: () -> Unit) {
    LazyColumn(state = LocalWalkthrough.current?.list ?: rememberLazyListState(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "dashboard_status") { Panel { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Circle, null, Modifier.size(10.dp), ShelterSafe); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Heading(if (isSealing) "Sealing evidence" else if (emergency) "Recording in progress" else if (listeningActive && gestureWakeMode) "Safeword listening • ${gestureWindowSecondsRemaining}s" else if (gestureWindowOpening) "Opening microphone…" else if (listening) "Safeword armed" else "Safeword paused"); Caption(stringResource(R.string.ui_evidence_stays_on_this_device)) }
            Badge(stringResource(R.string.ui_local))
        } } }
        item(key = "safeword") { Panel(Modifier.featureTarget(FeatureHelp.SAFEWORD), color = ShelterSurfaceRaised) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Mic, null, tint = ShelterSafe); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.ui_safeword_armed), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); Badge(if (!listening) "Paused" else if (gestureWindowOpening) "Opening" else if (gestureWakeMode && listeningActive) "Listening ${gestureWindowSecondsRemaining}s" else if (gestureWakeMode) "Gesture armed" else "Continuous Active") }
            ListeningBars(emergency, ShelterSafe, Modifier.align(Alignment.CenterHorizontally).height(32.dp))
            Caption(if (gestureWakeMode) stringResource(R.string.gesture_prompt) else "Listens while app is foregrounded")
            Surface(onClick = onEdit, shape = RoundedCornerShape(12.dp), color = ShelterSurface) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Caption(stringResource(R.string.ui_trigger_phrase)); Heading("“$safeword”") }; Icon(Icons.Outlined.EditNote, "Edit safeword")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.GraphicEq, null); Text(stringResource(R.string.ui_safeword_armed_2), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium); Switch(listening, onListen, enabled = !emergency && !isSealing) }
        } }
        item(key = "alert") { Panel(color = Color(0xFF31312D)) {
            Icon(Icons.Outlined.VerifiedUser, null, Modifier.align(Alignment.CenterHorizontally).size(40.dp), ShelterSafeSoft)
            Text(if (isSealing) "Encrypting and sealing evidence…" else if (emergency) "Your recording is running" else "Your quiet safety shield", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(stringResource(R.string.ui_capture_audio_locally_then_seal_it_with_encryption_and_a_tamper_e), color = Color(0xFFC1C8C3), style = MaterialTheme.typography.bodyMedium)
            when {
                isSealing -> Action(stringResource(R.string.ui_sealing_evidence), Icons.Outlined.Lock, {}, enabled = false)
                emergency -> Action(stringResource(R.string.ui_stop_seal_evidence), Icons.Outlined.Shield, onTrigger)
                else -> HoldToAlert(onTrigger)
            }
            Text(stringResource(R.string.ui_audio_stays_on_device_trusted_contacts_receive_one_sms), color = Color(0xFFC1C8C3), style = MaterialTheme.typography.labelMedium)
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) { Icon(Icons.Outlined.Contacts, null, tint = ShelterSafe); Heading(stringResource(R.string.ui_trusted_contacts)); Caption(if (trustedContactCount == 0) "Add contacts in Settings" else "$trustedContactCount configured"); Badge(stringResource(R.string.ui_sms_during_emergency)); Action(stringResource(R.string.ui_manage_contacts), Icons.Outlined.People, { navigate(4) }, primary = false) }
            Panel(Modifier.weight(1f)) { Icon(Icons.Outlined.Lock, null, tint = ShelterSafe); Heading(stringResource(R.string.ui_secure_vault)); Caption("$count sealed evidence items"); Badge(stringResource(R.string.ui_on_device)); Action(stringResource(R.string.ui_open_vault), Icons.Outlined.FolderSpecial, { navigate(1) }, primary = false) }
        } }
        item(key = "decoy") { Row(Modifier.featureTarget(FeatureHelp.DECOY), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) { Icon(Icons.Outlined.Phone, null, tint = ShelterSafe); Heading(stringResource(FeatureHelp.DECOY.title)); FeatureHelpLink(FeatureHelp.DECOY); Caption(if (decoyDelay > 0) "Scheduled in ${decoyDelay}s" else "Simulated incoming screen");
                Row { TextButton(onClick = { scheduleDecoy(0) }) { Text(stringResource(R.string.ui_now)) }; TextButton(onClick = { scheduleDecoy(15) }) { Text(stringResource(R.string.ui_15s)) }; TextButton(onClick = { scheduleDecoy(30) }) { Text(stringResource(R.string.ui_30s)) } } }
            Panel(Modifier.weight(1f)) { Icon(Icons.Outlined.HealthAndSafety, null, tint = ShelterSafe); Heading(stringResource(R.string.ui_safe_haven)); Caption(stringResource(R.string.ui_explore_community_routes)); Action(stringResource(R.string.ui_wayfinder), Icons.Outlined.NearMe, { navigate(2) }, primary = false) }
        } }
        item { Panel { Heading(stringResource(R.string.ui_community_outposts)); Caption(stringResource(R.string.ui_preview_nearby_support_locations_and_route_information)); Badge(stringResource(R.string.ui_sample_map_not_live)); Action(stringResource(R.string.ui_explore_routes), Icons.Outlined.Map, { navigate(2) }, primary = false) } }
        item { Panel { Heading(stringResource(R.string.ui_photo_video_evidence)); FeatureHelpLink(FeatureHelp.CAMERA); Action(stringResource(R.string.ui_open_private_camera), Icons.Outlined.PhotoCamera, openCamera, primary = false) } }
        item { Action(stringResource(R.string.ui_lock_to_notes), Icons.Outlined.VisibilityOff, disguise, primary = false) }
    }
}

@Composable
private fun HoldToAlert(onComplete: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).featureTarget(FeatureHelp.ALERT).pointerInput(onComplete) {
            detectTapGestures(onPress = {
                holding = true
                val releasedBeforeTwoSeconds = withTimeoutOrNull(2_000L) { tryAwaitRelease() }
                if (releasedBeforeTwoSeconds == null) onComplete()
                holding = false
            })
        },
        color = if (holding) ShelterSafe.copy(alpha = 0.72f) else ShelterSafe,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (holding) "Keep holding…" else "Hold 2s to alert trusted contacts & record evidence", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Vault(emergency: Boolean, isSealing: Boolean, label: String, score: Int, lat: Double?, lng: Double?, evidence: List<Evidence>, unlocked: Boolean,
    biometric: Boolean, trigger: () -> Unit, unlock: () -> Unit, verify: () -> Unit,
    playback: EvidencePlaybackState, togglePlayback: (Evidence) -> Unit, stopPlayback: () -> Unit,
    seekPlayback: (Evidence, Long) -> Unit, mediaPreview: EvidenceMediaPreviewState,
    openMedia: (Evidence) -> Unit, closeMedia: () -> Unit, export: (Evidence) -> Unit,
    deleteEvidence: (Evidence) -> Unit, blackout: () -> Unit, openCamera: () -> Unit,
    importMedia: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    var audioTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingDeletion by remember { mutableStateOf<Evidence?>(null) }
    val incidents = remember(evidence) { evidence.groupBy { it.incidentId ?: "legacy-${it.id}" }.entries.toList() }
    LazyColumn(state = LocalWalkthrough.current?.list ?: rememberLazyListState(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "vault") { Panel(Modifier.featureTarget(FeatureHelp.VAULT)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, tint = ShelterSafe); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Heading(stringResource(R.string.ui_stealth_evidence_vault)); Caption(stringResource(R.string.ui_on_device_aes_256_gcm)) }; Badge(if (unlocked) "Unlocked" else "Locked") } } }
        item { Panel(color = ShelterSurfaceRaised) {
            Heading(if (isSealing) "SEALING EVIDENCE" else if (emergency) "MIC RECORDING" else "LOCAL CAPTURE")
            ListeningBars(emergency, ShelterSafe, Modifier.align(Alignment.CenterHorizontally).height(40.dp))
            Text(if (isSealing) "Encrypting and committing…" else if (emergency) "Recording…" else "Ready when you are", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall)
            Caption(if (isSealing) "New recordings are blocked until sealing finishes" else if (emergency) "Auto-seals after 5 minutes" else "Start recording from Spaces")
            TabRow(selectedTabIndex = audioTab, containerColor = ShelterSurfaceRaised) {
                listOf("Audio", "Location").forEachIndexed { i, title -> Tab(audioTab == i, onClick = { audioTab = i }, text = { Text(title) }) }
            }
            if (audioTab == 0) ThreatMeter(label, score) else Caption(if (lat != null && lng != null) "Last sealed location: $lat, $lng" else "A location fix is requested when evidence is sealed.")
        } }
        item { Action(stringResource(R.string.feature_blackout_title), Icons.Outlined.VisibilityOff, blackout, primary = false) }
        item(key = "media") { Row(Modifier.fillMaxWidth().featureTarget(FeatureHelp.CAMERA), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = openCamera, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_capture)) }
            OutlinedButton(onClick = importMedia, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_import)) }
        } }
        if (emergency) item { Action(stringResource(R.string.ui_stop_seal_vault), Icons.Outlined.Lock, trigger) }
        if (isSealing) item { Action(stringResource(R.string.ui_sealing_evidence), Icons.Outlined.Lock, {}, enabled = false) }
        item { Panel { Heading(stringResource(R.string.ui_offline_first_storage)); FeatureHelpLink(FeatureHelp.VAULT) } }
        item { Heading(stringResource(R.string.ui_encrypted_evidence_log)) }
        if (!unlocked) item { Panel(color = ShelterSurfaceRaised) {
            Icon(Icons.Outlined.Fingerprint, null, Modifier.size(36.dp), ShelterSafe)
            Heading(stringResource(R.string.ui_your_evidence_is_private))
            Caption(if (biometric) "Authenticate to view sealed recordings." else "Set up a device screen lock or biometrics to access the vault.")
            Button(onClick = unlock, enabled = biometric, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_unlock_vault)) }
        } } else {
            item { Action(stringResource(R.string.feature_integrity_title), Icons.Outlined.VerifiedUser, verify) }
            if (evidence.isEmpty()) item { Panel { Heading(stringResource(R.string.ui_no_sealed_recordings_yet)); Caption(stringResource(R.string.ui_record_from_spaces_then_stop_and_seal_to_create_your_first_entry)) } }
            items(incidents, key = { it.key }) { incident ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("INCIDENT ${incident.key.takeLast(8)}", style = MaterialTheme.typography.labelLarge, color = ShelterSafe)
                            Caption("${incident.value.size} sealed item${if (incident.value.size == 1) "" else "s"}")
                        }
                        Badge(stringResource(R.string.ui_hash_chained))
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
                    summary = IncidentSummary.describe(entry),
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
            title = { Text(if (entry.deletedFileHash == null) "Delete evidence?" else "Retry deletion?") },
            text = { Text("The encrypted ${entry.mediaType.lowercase()} file will be permanently removed from this device. Its hash-only deletion record stays in the chain, so later evidence can still be verified.") },
            confirmButton = {
                TextButton(onClick = { pendingDeletion = null; deleteEvidence(entry) }) {
                    Text(stringResource(R.string.ui_delete_permanently), color = ShelterDanger)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }
}

@Composable
private fun MediaPreview(state: EvidenceMediaPreviewState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ShelterSurface,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Heading(if (state.mediaType.equals("VIDEO", true)) "Video evidence" else "Image evidence")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close preview") }
                }
                when {
                    state.isPreparing -> Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.filePath == null -> Caption(stringResource(R.string.ui_preview_unavailable))
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
                        if (bitmap == null) Caption(stringResource(R.string.ui_this_image_could_not_be_decoded)) else Image(
                            bitmap = bitmap,
                            contentDescription = "Decrypted evidence image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                        )
                    }
                }
                Caption(stringResource(R.string.ui_temporary_decrypted_preview_removed_when_this_window_closes))
            }
        }
    }
}

@Composable
private fun NotesScreen(unlock: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vaani_notes", 0) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All Notes") }
    var note by rememberSaveable { mutableStateOf(prefs.getString("memo", "") ?: "") }
    var editor by remember { mutableStateOf(false) }
    val groceries = listOf("Cold-pressed olive oil & balsamic vinegar", "Organic unsweetened almond milk", "Artisan sourdough boule (sliced)", "Fresh mint leaves & baby spinach", "Loose leaf chamomile tea")
    val checked = remember { mutableStateListOf(*Array(5) { prefs.getBoolean("grocery_$it", it < 2) }) }
    fun matches(title: String, group: String) = (category == "All Notes" || category == group) && title.contains(query, true)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { OutlinedTextField(query, { value ->
            if (value.trim().lowercase(Locale.ROOT) in disguisePasscodes) {
                query = ""
                unlock()
            } else query = value
        }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.ui_search_memos_recipes_lists)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp), singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All Notes", "Groceries", "Reading", "Work Sync").forEach { label -> FilterChip(category == label, { category = label }, label = { Text(label) }) } } }
        if (matches("Weekly Grocery & Market", "Groceries")) item { Panel {
            Heading(stringResource(R.string.ui_weekly_grocery_market)); Caption(stringResource(R.string.ui_items_for_saturday_brunch_prep_weekly_staples_from_the_corner_mar))
            groceries.forEachIndexed { i, text -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked[i], { checked[i] = it; prefs.edit().putBoolean("grocery_$i", it).apply() }); Text(text, style = MaterialTheme.typography.bodyMedium, textDecoration = if (checked[i]) TextDecoration.LineThrough else null) } }
            Caption("${checked.count { it }} of 5 done  •  Groceries")
        } }
        if (matches("Reading Journal", "Reading")) item { Panel { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(32.dp), ShelterSafe); Heading(stringResource(R.string.ui_reading_journal)); Caption(stringResource(R.string.ui_in_praise_of_shadows_essays_on_quiet_architecture_domestic_ritual)); Badge(stringResource(R.string.ui_personal_3_recommendations)) } }
        if (matches("Tuesday Sync: Platform Review", "Work Sync")) item { Panel { Heading(stringResource(R.string.ui_tuesday_sync_platform_review)); Caption(stringResource(R.string.ui_action_items_from_product_design_alignment_and_quarterly_review)); Text(stringResource(R.string.ui_standardize_typography_across_the_app_review_the_memo_input_exper)); Caption(stringResource(R.string.ui_updated_by_sarah_work_sync)) } }
        if (matches("Roasted Butternut Soup", "Groceries")) item { Panel { Heading(stringResource(R.string.ui_roasted_butternut_soup)); Caption(stringResource(R.string.ui_caramelize_with_nutmeg_brown_butter_shallots_and_crisp_sage)); Badge(stringResource(R.string.ui_kitchen_45_mins)) } }
        if (note.isNotBlank() && matches(note, "All Notes")) item { Panel { Heading(stringResource(R.string.ui_my_memo)); Text(note); TextButton(onClick = { editor = true }) { Text(stringResource(R.string.ui_edit_memo)) } } }
        item { Action(stringResource(R.string.ui_write_a_memo), Icons.Outlined.EditNote, { editor = true }) }
    }
    if (editor) AlertDialog(onDismissRequest = { editor = false }, title = { Text(stringResource(R.string.ui_my_memo)) }, text = { OutlinedTextField(note, { note = it }, minLines = 4, label = { Text(stringResource(R.string.ui_note)) }) },
        confirmButton = { TextButton(onClick = { prefs.edit().putString("memo", note).apply(); editor = false }) { Text(stringResource(R.string.ui_save)) } })
}

private fun nearbyDangerZone(latitude: Double?, longitude: Double?, zones: List<MapDangerZone>): MapDangerZone? {
    if (latitude == null || longitude == null) return null
    return zones
        .minByOrNull { distanceMeters(latitude, longitude, it.latitude, it.longitude) }
        ?.takeIf { distanceMeters(latitude, longitude, it.latitude, it.longitude) <= it.radiusMeters }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LeafletMap(
    filter: String,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    locationTimestamp: Long?,
    zones: List<MapDangerZone>,
    recenterRequest: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapError by remember { mutableStateOf<String?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var mapLoadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(mapLoadAttempt) {
        delay(8_000L)
        if (!mapReady && mapError == null) mapError = "Map did not finish loading. Tap Retry to reload it."
    }
    val checkMapSize: (WebView) -> Unit = { view ->
        if (view.width > 0 && view.height > 0) {
            view.evaluateJavascript(
                "window.invalidateMapSize && window.invalidateMapSize(); Boolean(window.isMapReady && window.isMapReady());"
            ) { result ->
                if (result == "true" || result == "\"true\"") {
                    mapReady = true
                    mapError = null
                }
            }
        }
    }
    val webView = remember(context, mapLoadAttempt) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.userAgentString = "${settings.userAgentString} VAANI-Android/1.0"
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMapReady() {
                    (context as? android.app.Activity)?.runOnUiThread {
                        mapReady = true
                        mapError = null
                    } ?: run {
                        mapReady = true
                        mapError = null
                    }
                }
            }, "AndroidBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e("VaaniMap", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.scheme != "https" || request.url.host != "appassets.androidplatform.net") return null
                    val path = request.url.path.orEmpty()
                    val asset = path.removePrefix("/assets/")
                    if (!path.startsWith("/assets/") || asset.contains("..") ||
                        (asset != "leaflet_map.html" && !asset.startsWith("leaflet/"))) {
                        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                    val mime = when {
                        asset.endsWith(".html") -> "text/html"
                        asset.endsWith(".css") -> "text/css"
                        asset.endsWith(".js") -> "text/javascript"
                        asset.endsWith(".png") -> "image/png"
                        else -> "text/plain"
                    }
                    return try {
                        WebResourceResponse(mime, if (mime.startsWith("text/")) "UTF-8" else null, context.assets.open(asset))
                    } catch (_: IOException) {
                        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    (view.tag as? String)?.let { view.evaluateJavascript(it, null) }
                    view.post { checkMapSize(view) }
                    view.postDelayed({ checkMapSize(view) }, 500L)
                    view.postDelayed({ checkMapSize(view) }, 1500L)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        mapReady = false
                        mapError = "Map page could not load. Tap Retry to try again."
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            }
            addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right > left && bottom > top) {
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        view.post { checkMapSize(view as WebView) }
                    }
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/leaflet_map.html")
        }
    }
    val validLocation = latitude?.isFinite() == true && longitude?.isFinite() == true
    val zonesJson = remember(zones) { dangerZonesJson(zones) }
    val script = remember(filter, latitude, longitude, accuracyMeters, locationTimestamp, zonesJson, recenterRequest) {
        val lat = if (validLocation) latitude.toString() else "null"
        val lng = if (validLocation) longitude.toString() else "null"
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.toString() ?: "null"
        val timestamp = locationTimestamp?.takeIf { it > 0L }?.toString() ?: "null"
        buildString {
            append("window.applyNativeState && window.applyNativeState(")
            append(JSONObject.quote(filter)).append(", ").append(lat).append(", ").append(lng)
            append(", ").append(accuracy).append(", ").append(timestamp).append(", ").append(zonesJson).append(");")
            append("window.requestRecenter && window.requestRecenter(").append(recenterRequest).append(");")
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
        }
    }
    Box(modifier) {
        AndroidView(
            factory = { webView },
            update = { view ->
                if (view.tag != script) {
                    view.tag = script
                    view.evaluateJavascript(script, null)
                }
                view.post { checkMapSize(view) }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (!mapReady) {
            Column(
                Modifier.fillMaxSize().background(ShelterSurface).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (mapError == null) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ui_loading_community_map))
                } else {
                    Text(mapError ?: "Map unavailable")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        mapError = null
                        mapReady = false
                        mapLoadAttempt++
                    }) { Text(stringResource(R.string.ui_retry_map)) }
                }
            }
        }
    }
}

@Composable
private fun Community(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    locationTimestamp: Long?,
    refreshLocation: () -> Unit,
    info: (String) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("All Signals") }
    var factor by rememberSaveable { mutableStateOf("Dark stretch") }
    var reports by rememberSaveable { mutableIntStateOf(0) }
    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val zones = remember(latitude, longitude) { adaptiveDangerZones(latitude, longitude) }
    val nearbyZone = remember(latitude, longitude, zones) { nearbyDangerZone(latitude, longitude, zones) }
    val tourScope = LocalWalkthrough.current
    LaunchedEffect(tourScope?.active) { if (tourScope?.active != true) refreshLocation() }
    LaunchedEffect(nearbyZone?.title, tourScope?.active) {
        if (tourScope?.active != true) nearbyZone?.let { info("Caution: you are inside the sample ${it.title.lowercase()} zone. Check local conditions and choose a well-lit route.") }
    }
    LazyColumn(state = LocalWalkthrough.current?.list ?: rememberLazyListState(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Badge(stringResource(R.string.ui_leaflet_adaptive_sample_incident_history)); Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.LocationOn, "Current location", tint = ShelterSafe) } }
        if (nearbyZone != null) item { Panel(color = ShelterDanger.copy(alpha = 0.18f)) { Heading("Caution near ${nearbyZone.title.lowercase()}"); Caption(stringResource(R.string.ui_your_current_location_overlaps_this_sample_risk_area_use_the_map_)) } }
        item(key = "map") {
            LeafletMap(
                filter,
                latitude,
                longitude,
                accuracyMeters,
                locationTimestamp,
                zones,
                recenterRequest,
                Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(20.dp)).featureTarget(FeatureHelp.MAP)
            )
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = refreshLocation, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_refresh)) }
            OutlinedButton(onClick = {
                if (latitude != null && longitude != null) recenterRequest++ else refreshLocation()
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_recenter)) }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All Signals", "Well-Lit Streets", "Patrol Points").forEach { label -> FilterChip(filter == label, { filter = label }, label = { Text(label) }) } } }
        item { Panel { Heading(stringResource(R.string.ui_safe_corridor_preview)); Badge(stringResource(R.string.ui_adaptive_sample_data)); Panel(color = ShelterSurfaceRaised) { Heading(stringResource(R.string.ui_suggested_nearby_well_lit_corridor)); Caption(stringResource(R.string.ui_leaflet_route_overlay_near_the_current_map_area_no_live_safety_sc)); Action(stringResource(R.string.ui_open_device_maps), Icons.Outlined.NearMe, { openLink(context, "geo:0,0?q=nearby+police+station") }) }; Caption(stringResource(R.string.ui_map_tiles_require_internet_access_route_availability_and_local_co)) } }
        item { Heading(stringResource(R.string.ui_community_observations)) }
        item { Panel { Heading(stringResource(R.string.ui_night_transit_shuttle)); Caption(stringResource(R.string.ui_sample_gate_4_station_regular_service)) } }
        item { Panel {
            Heading(stringResource(R.string.ui_report_environmental_factor)); Caption(stringResource(R.string.ui_try_a_local_demo_report_nothing_is_published_or_sent))
            listOf("Dark stretch", "Isolated alley", "Harassment hotspot", "Stray dogs").chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { text -> FilterChip(factor == text, { factor = text }, label = { Text(text, fontSize = 12.sp) }) } } }
            Action(stringResource(R.string.ui_save_demo_signal), Icons.Outlined.AddLocationAlt, { reports++; info("Demo signal saved for this session: $factor. It has not been shared.") })
            Caption("$reports local demo signals this session")
        } }
        item { PreviewActionPanel("Nearby police stations", "Preview nearby public stations. This does not notify or dispatch police.", Icons.Outlined.LocalPolice) { info("Preview: no alert was sent. Opening nearby police stations in your device map."); openLink(context, "geo:0,0?q=nearby+police+station") } }
        item { PreviewActionPanel("Nearby NGOs", "Preview local support organizations. No organization is contacted from this panel.", Icons.Outlined.VolunteerActivism) { info("Preview: no alert was sent. Connect an approved recipient integration before using this for dispatch.") } }
        item { PreviewActionPanel("Peer support", "Preview consenting peer-support options. No peer is contacted from this panel.", Icons.Outlined.Groups) { info("Preview: no alert was sent. Peer support requires an approved, consent-based integration.") } }
        item { PreviewActionPanel("Nearby shelters", "Preview local shelters and public resources. No shelter is contacted from this panel.", Icons.Outlined.HomeWork) { info("Preview: no alert was sent. Opening this preview does not reserve a place or notify a shelter.") } }
    }
}

private fun openLink(context: android.content.Context, uri: String) {
    try { context.startActivity(Intent(if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW, Uri.parse(uri))) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No app available to open this action", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun Support(disguise: () -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf("") }
    LazyColumn(state = LocalWalkthrough.current?.list ?: rememberLazyListState(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "support") { Panel(Modifier.featureTarget(FeatureHelp.SUPPORT)) { Badge(stringResource(R.string.ui_vaani_care_confidential)); Heading(stringResource(R.string.ui_support_when_you_need_it)); FeatureHelpLink(FeatureHelp.SUPPORT) } }
        item { Heading(stringResource(R.string.ui_emergency_support_india)) }
        item { Panel(color = ShelterSurfaceRaised) { Icon(Icons.Outlined.HealthAndSafety, null, tint = ShelterSafe); Heading(stringResource(R.string.ui_emergency_assistance)); Caption(stringResource(R.string.ui_india_s_emergency_response_number_for_police_fire_and_medical_eme)); Action(stringResource(R.string.ui_dial_112), Icons.Outlined.Phone, { openLink(context, "tel:112") }); TextButton(onClick = { openLink(context, "https://112.gov.in/") }) { Text(stringResource(R.string.ui_official_service_information)) } } }
        item { Panel { Heading(stringResource(R.string.ui_legal_aid_assistance)); Caption(stringResource(R.string.ui_national_legal_services_authority_nalsa)); Badge(stringResource(R.string.ui_public_helpline)); Action(stringResource(R.string.ui_dial_15100), Icons.Outlined.Gavel, { openLink(context, "tel:15100") }, primary = false); TextButton(onClick = { openLink(context, "https://nalsa.gov.in/womens-assistance/") }) { Text(stringResource(R.string.ui_legal_assistance_resources)) } } }
        item { Panel { Heading(stringResource(R.string.ui_cyber_safety_harassment)); Caption(stringResource(R.string.ui_use_the_national_cyber_crime_reporting_portal_to_report_cybercrim)); Action(stringResource(R.string.ui_open_reporting_portal), Icons.Outlined.Security, { openLink(context, "https://cybercrime.gov.in/") }, primary = false); TextButton(onClick = { openLink(context, "tel:1930") }) { Text(stringResource(R.string.ui_dial_1930_financial_fraud)) } } }
        item { Heading(stringResource(R.string.ui_support_guides)) }
        listOf("Finding legal assistance" to "Explore NALSA's official assistance page for available services and eligibility.", "Digital privacy" to "Review app permissions and linked devices in your device settings. The official cybercrime portal provides reporting resources.", "Finding local care" to "Use device maps to search for nearby hospitals. In an emergency in India, open the dialer for 112.").forEach { (title, text) ->
            item { Panel(color = ShelterSurfaceRaised) { TextButton(onClick = { expanded = if (expanded == title) "" else title }) { Text(title, Modifier.weight(1f)); Icon(if (expanded == title) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }; if (expanded == title) Caption(text) } }
        }
        item { Action(stringResource(R.string.ui_quick_exit_to_notes), Icons.Outlined.VisibilityOff, disguise, primary = false) }
    }
}

@Composable
private fun Settings(safeword: String, listening: Boolean, onListen: (Boolean) -> Unit,
    gestureWakeMode: Boolean, onGestureWakeModeChange: (Boolean) -> Unit, edit: () -> Unit,
    disguiseEnabled: Boolean, onDisguiseEnabledChange: (Boolean) -> Unit, disguise: () -> Unit, info: (String) -> Unit,
    trustedContacts: List<TrustedContact>, smsStatuses: List<ContactAlertStatus>, locationTracking: Boolean,
    cachedLocation: CachedLocation?, onAddContact: (String, String) -> Unit, onRemoveContact: (String) -> Unit,
    onPickContact: () -> Unit, onLocationTrackingChange: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit, onImportMedia: () -> Unit, onCaptureMedia: () -> Unit,
    replay: () -> Unit, featureHelp: () -> Unit, tourEnabled: Boolean) {
    val context = LocalContext.current
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactNumber by rememberSaveable { mutableStateOf("") }
    LazyColumn(state = LocalWalkthrough.current?.list ?: rememberLazyListState(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "help") { Panel(Modifier.featureTarget(FeatureHelp.REPLAY)) {
            Heading(stringResource(R.string.tour_help))
            Button(onClick = replay, enabled = tourEnabled) { Text(stringResource(R.string.tour_replay)) }
            TextButton(onClick = featureHelp) { Text(stringResource(R.string.tour_feature_help)) }
            if (!tourEnabled) Caption(stringResource(R.string.tour_busy))
        } }
        item { Panel { Heading(stringResource(R.string.ui_protection_preferences)); Caption(stringResource(R.string.ui_local_capture_notes_disguise)); Badge(if (listening) "Listening" else "Paused") } }
        item { Heading(stringResource(R.string.ui_acoustic_trigger)) }
        item { Panel(color = ShelterSurfaceRaised) {
            Caption(stringResource(R.string.ui_current_safeword_phrase))
            Heading("“$safeword”")
            Action(stringResource(R.string.ui_edit_safeword), Icons.Outlined.Edit, edit, primary = false)
            Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.ui_safeword_protection), Modifier.weight(1f)); Switch(listening, onListen) }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (gestureWakeMode) "Stealth Mode (Gesture-Wake 12s Window)" else "Continuous Listening", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(gestureWakeMode, onGestureWakeModeChange)
            }
            FeatureHelpLink(FeatureHelp.GESTURE)
            FeatureHelpLink(FeatureHelp.SAFEWORD)
        } }
        item { Heading(stringResource(R.string.ui_device_vault_security)) }
        item { Panel { Heading(stringResource(R.string.ui_biometric_vault_lock)); FeatureHelpLink(FeatureHelp.SECURITY); Action(stringResource(R.string.ui_device_security_settings), Icons.Outlined.Fingerprint, { context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }, primary = false) } }
        item { Panel { Heading(stringResource(R.string.feature_location_title)); FeatureHelpLink(FeatureHelp.LOCATION); Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.ui_track_while_armed), Modifier.weight(1f)); Switch(locationTracking, onLocationTrackingChange) }; Caption(if (cachedLocation == null) "No location fix cached yet" else "Last fix: ${Date(cachedLocation.timestampMillis)}") } }
        item(key = "contacts") { Panel(Modifier.featureTarget(FeatureHelp.CONTACTS)) { Heading(stringResource(FeatureHelp.CONTACTS.title)); FeatureHelpLink(FeatureHelp.CONTACTS); trustedContacts.forEach { contact -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(contact.name); Caption(contact.number) }; TextButton(onClick = { onRemoveContact(contact.number) }) { Text(stringResource(R.string.ui_remove)) } } }; OutlinedTextField(contactName, { contactName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ui_name)) }, singleLine = true); OutlinedTextField(contactNumber, { contactNumber = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ui_phone_number)) }, singleLine = true); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { onAddContact(contactName, contactNumber); contactName = ""; contactNumber = "" }) { Text(stringResource(R.string.ui_add)) }; OutlinedButton(onClick = onPickContact) { Text(stringResource(R.string.ui_pick_contact)) } }; OutlinedButton(onClick = onRequestSmsPermission, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_allow_emergency_sms)) }; smsStatuses.forEach { status -> Caption("${status.name}: ${status.status}") } } }
        item { Panel { Heading(stringResource(R.string.ui_camera_evidence)); FeatureHelpLink(FeatureHelp.CAMERA); Action(stringResource(R.string.ui_open_private_camera), Icons.Outlined.PhotoCamera, onCaptureMedia); Action(stringResource(R.string.ui_import_image_or_video), Icons.Outlined.FileOpen, onImportMedia, primary = false) } }
        item { Heading(stringResource(R.string.ui_decoy_app_persona)) }
        item(key = "disguise") { Panel(Modifier.featureTarget(FeatureHelp.DISGUISE), color = ShelterSurfaceRaised) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_disguise_as_notes_app), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(disguiseEnabled, onDisguiseEnabledChange)
            }
            FeatureHelpLink(FeatureHelp.DISGUISE)
            Action(stringResource(R.string.ui_lock_to_notes_now), Icons.Outlined.VisibilityOff, disguise, primary = false)
        } }
        item { Heading(stringResource(R.string.ui_language_dispatch)) }
        item { Panel { Heading(stringResource(R.string.feature_dispatch_title)); FeatureDescription(FeatureHelp.DISPATCH); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(ShelterAmber)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_preview_action)) }; TextButton(onClick = { info("Preview: no police or NGO alert was sent.") }) { Text(stringResource(R.string.ui_about_preview_actions)) } } }
        item { Action(stringResource(R.string.ui_test_interface_quietly), Icons.Outlined.CheckCircleOutline, { info("Interface check complete. No recording was started and no contacts were notified. To test real recording, use the hold control on Armed.") }) }
    }
}

