package com.bithead.shelter.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bithead.shelter.R
import com.bithead.shelter.ui.components.BrutalPanel
import com.bithead.shelter.mesh.*
import com.bithead.shelter.ui.walkthrough.LocalWalkthrough
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun RealtimeMeshPanel(location: MeshLocation?, refreshLocation: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(context) { BluetoothMeshController(context) }
    val source = remember(context) { LocalMeshProfileSource(context) }
    val saved = remember { source.read() }
    var name by rememberSaveable { mutableStateOf(saved.displayName) }
    var phone by rememberSaveable { mutableStateOf(saved.phone) }
    var email by rememberSaveable { mutableStateOf(saved.email) }
    var shareEmail by rememberSaveable { mutableStateOf(saved.shareEmail) }
    var sharePhone by rememberSaveable { mutableStateOf(saved.sharePhone) }
    var shareLocation by rememberSaveable { mutableStateOf(saved.shareLocation) }
    var pendingStart by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var alive by remember { mutableStateOf(true) }
    var localMessage by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val state by controller.state.collectAsStateWithLifecycle()
    val busy = state.phase != MeshPhase.STOPPED
    val tour = LocalWalkthrough.current
    val hasLocation = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    SideEffect { controller.updateLocation(location) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (alive) {
            if (controller.hasPermissions() && (!shareLocation || hasLocation())) pendingStart = true
            else localMessage = R.string.mesh_permission_denied
        }
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (alive) {
            if (controller.bluetoothEnabled()) pendingStart = true else localMessage = R.string.mesh_bluetooth_off
        }
    }
    DisposableEffect(controller, lifecycle) {
        alive = true
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_STOP) { pendingStart = false; controller.stop() }
        }
        lifecycle.addObserver(observer)
        val accountObserver = source.observeAccountChanges {
            pendingStart = false
            controller.stop()
            val profile = source.read()
            name = profile.displayName; phone = profile.phone
            email = profile.email; shareEmail = profile.shareEmail
            sharePhone = profile.sharePhone; shareLocation = profile.shareLocation
        }
        onDispose { alive = false; accountObserver.close(); lifecycle.removeObserver(observer); controller.stop() }
    }
    LaunchedEffect(tour?.active) {
        if (tour?.active == true) { pendingStart = false; controller.stop() }
    }
    LaunchedEffect(pendingStart, resumed) {
        if (!pendingStart || !resumed || tour?.active == true) return@LaunchedEffect
        pendingStart = false
        localMessage = 0
        if (!controller.hasBleHardware()) { localMessage = R.string.mesh_unsupported; return@LaunchedEffect }
        val owner = MeshOwnerProfile(name.trim(), phone.trim(), sharePhone, shareLocation, email.trim(), shareEmail)
        if (!MeshCodec.validName(owner.displayName) || !MeshCodec.validPhone(owner.phone) || !MeshCodec.validEmail(owner.email)) {
            localMessage = R.string.mesh_invalid_profile
            return@LaunchedEffect
        }
        val needed = BluetoothMeshController.requiredPermissions().toMutableSet()
        if (shareLocation) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!controller.hasPermissions() || (shareLocation && !hasLocation())) permissions.launch(needed.toTypedArray())
        else if (!controller.bluetoothEnabled()) {
            runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { localMessage = R.string.mesh_bluetooth_off }
        }
        else {
            source.save(owner)
            controller.start(owner)
        }
    }
    LaunchedEffect(state.phase, shareLocation) {
        if (state.phase == MeshPhase.ACTIVE && shareLocation) {
            while (true) { refreshLocation(); delay(15_000L) }
        }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == MeshPhase.ACTIVE) while (true) { now = System.currentTimeMillis(); delay(2_000) }
    }

    BrutalPanel(header = stringResource(R.string.mesh_title)) {
        Text(stringResource(R.string.mesh_explanation), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mesh_profile_future), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it.take(48) }, label = { Text(stringResource(R.string.mesh_name)) },
            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it.take(32) }, label = { Text(stringResource(R.string.mesh_phone)) },
            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(email, { email = it.take(120) }, label = { Text(stringResource(R.string.mesh_email)) },
            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Row { Checkbox(shareEmail, { shareEmail = it }, enabled = !busy); Text(stringResource(R.string.mesh_share_email), Modifier.padding(top = 12.dp)) }
        Row { Checkbox(sharePhone, { sharePhone = it }, enabled = !busy); Text(stringResource(R.string.mesh_share_phone), Modifier.padding(top = 12.dp)) }
        Row { Checkbox(shareLocation, { shareLocation = it }, enabled = !busy); Text(stringResource(R.string.mesh_share_location), Modifier.padding(top = 12.dp)) }
        Text(stringResource(R.string.mesh_consent), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(when (state.phase) {
            MeshPhase.STOPPED -> R.string.mesh_stopped
            MeshPhase.STARTING -> R.string.mesh_starting
            MeshPhase.ACTIVE -> R.string.mesh_active
        }))
        state.problem?.let { problem ->
            Text(stringResource(when (problem) {
                MeshProblem.PERMISSION -> R.string.mesh_permission_denied
                MeshProblem.BLUETOOTH_OFF -> R.string.mesh_bluetooth_off
                MeshProblem.UNSUPPORTED -> R.string.mesh_unsupported
                MeshProblem.LOCATION_OFF -> R.string.mesh_location_off
                MeshProblem.ADVERTISE -> R.string.mesh_advertise_failed
                MeshProblem.SCAN -> R.string.mesh_scan_failed
                MeshProblem.STARTUP -> R.string.mesh_start_failed
            }), color = MaterialTheme.colorScheme.error)
        }
        if (localMessage != 0) Text(stringResource(localMessage), color = MaterialTheme.colorScheme.error)
        if (busy) {
            OutlinedButton(onClick = { pendingStart = false; controller.stop() }) { Text(stringResource(R.string.mesh_stop)) }
        } else {
            Button(onClick = { pendingStart = true }, enabled = !pendingStart && tour?.active != true) {
                Text(stringResource(R.string.mesh_start))
            }
        }
        if (state.problem == MeshProblem.PERMISSION || localMessage == R.string.mesh_permission_denied) {
            TextButton(onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)))
            }) { Text(stringResource(R.string.mesh_settings)) }
        }
        if (state.problem == MeshProblem.LOCATION_OFF) {
            TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text(stringResource(R.string.mesh_location_settings))
            }
        }
        if (busy) {
            Text(stringResource(R.string.mesh_peer_count, state.peers.size), style = MaterialTheme.typography.titleMedium)
            if (state.peers.isEmpty()) Text(stringResource(R.string.mesh_empty))
        }
        state.peers.forEach { peer -> key(peer.profile.sessionId) {
            HorizontalDivider()
            Text(peer.profile.displayName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.mesh_unverified), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.mesh_seen, ((android.os.SystemClock.elapsedRealtime() - peer.lastSeenElapsed) / 1000).coerceAtLeast(0)),
                style = MaterialTheme.typography.bodySmall)
            val point = peer.profile.location?.takeIf { it.isFresh(now) }
            if (point == null) Text(stringResource(R.string.mesh_location_hidden))
            else {
                Text(stringResource(R.string.mesh_coordinates, String.format(Locale.ROOT, "%.3f", point.latitude),
                    String.format(Locale.ROOT, "%.3f", point.longitude), point.accuracyMeters.toInt()))
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}"))) }
                }) { Text(stringResource(R.string.mesh_open_map)) }
            }
            peer.profile.phone?.let { number ->
                Text(number)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
                }) { Text(stringResource(R.string.mesh_dial)) }
            } ?: Text(stringResource(R.string.mesh_contact_hidden))
            peer.profile.email?.let { emailAddress ->
                Text(emailAddress)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", emailAddress, null))) }
                }) { Text(stringResource(R.string.mesh_email_action)) }
            }
        } }
    }
}
