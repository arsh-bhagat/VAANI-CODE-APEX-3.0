package com.bithead.shelter.ui.screens

import android.content.Intent
import android.location.Location
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bithead.shelter.data.TrustedContact
import com.bithead.shelter.emergency.CachedLocation
import com.bithead.shelter.emergency.ContactAlertStatus
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.theme.*
import java.util.Date

@Composable
internal fun SettingsScreen(safeword: String, listening: Boolean, onListen: (Boolean) -> Unit,
    gestureWakeMode: Boolean, onGestureWakeModeChange: (Boolean) -> Unit, edit: () -> Unit,
    disguiseEnabled: Boolean, onDisguiseEnabledChange: (Boolean) -> Unit, disguise: () -> Unit, info: (String) -> Unit,
    trustedContacts: List<TrustedContact>, smsStatuses: List<ContactAlertStatus>, locationTracking: Boolean,
    cachedLocation: CachedLocation?, onAddContact: (String, String) -> Unit, onRemoveContact: (String) -> Unit,
    onPickContact: () -> Unit, onLocationTrackingChange: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit, onImportMedia: () -> Unit, onCaptureMedia: () -> Unit) {
    val context = LocalContext.current
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactNumber by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Panel { Heading("Protection preferences"); Caption("Local capture • notes disguise"); Badge(if (listening) "Listening" else "Paused") } }
        item { Heading("ACOUSTIC TRIGGER") }
        item { Panel(color = ShelterSurfaceRaised) {
            Caption("Current safeword phrase")
            Heading("“$safeword”")
            Action("Edit safeword", Icons.Outlined.Edit, edit, primary = false)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Safeword Protection", Modifier.weight(1f)); BrutalSwitch(listening, onListen, label = "Safeword protection") }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (gestureWakeMode) "Stealth Mode (Gesture-Wake 12s Window)" else "Continuous Listening", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                BrutalSwitch(gestureWakeMode, onGestureWakeModeChange, label = "Gesture wake mode")
            }
            Caption("Stealth mode keeps the mic off until a physical jerk is detected. Gesture detection works while VAANI or Notes is open in the foreground.")
            Caption("Offline recognition depends on your device.")
        } }
        item { Heading("DEVICE & VAULT SECURITY") }
        item { Panel { Heading("Biometric vault lock"); Caption("Uses your device fingerprint, face, or screen lock. The vault locks when the app leaves the foreground."); Action("Device security settings", Icons.Outlined.Fingerprint, { context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }, primary = false) } }
        item { Panel { Heading("Location protection"); Caption("Keeps a timestamped last known location ready for emergency SMS. Android shows an ongoing notification while active."); Row(verticalAlignment = Alignment.CenterVertically) { Text("Track while armed", Modifier.weight(1f)); BrutalSwitch(locationTracking, onLocationTrackingChange, label = "Location tracking") }; Caption(if (cachedLocation == null) "No location fix cached yet" else "Last fix: ${Date(cachedLocation.timestampMillis)}") } }
        item { Panel { Heading("Trusted contacts"); Caption("Emergency SMS sends only to contacts you add here."); trustedContacts.forEach { contact -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(contact.name); Caption(contact.number) }; TextButton(onClick = { onRemoveContact(contact.number) }) { Text("Remove") } } }; OutlinedTextField(contactName, { contactName = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true); OutlinedTextField(contactNumber, { contactNumber = it }, Modifier.fillMaxWidth(), label = { Text("Phone number") }, singleLine = true); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { onAddContact(contactName, contactNumber); contactName = ""; contactNumber = "" }) { Text("Add") }; OutlinedButton(onClick = onPickContact) { Text("Pick contact") } }; OutlinedButton(onClick = onRequestSmsPermission, modifier = Modifier.fillMaxWidth()) { Text("Allow emergency SMS") }; smsStatuses.forEach { status -> Caption("${status.name}: ${status.status}") } } }
        item { Panel { Heading("Camera evidence"); Caption("Capture private photos or silent videos up to 60 seconds, then seal them in the encrypted vault."); Action("Open private camera", Icons.Outlined.PhotoCamera, onCaptureMedia); Action("Import image or video", Icons.Outlined.FileOpen, onImportMedia, primary = false) } }
        item { Heading("DECOY APP PERSONA") }
        item { Panel(color = ShelterSurfaceRaised) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Disguise as Notes App", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                BrutalSwitch(disguiseEnabled, onDisguiseEnabledChange, label = "Notes launcher disguise")
            }
            Caption(if (disguiseEnabled) "The launcher appears as Notes and future launches open All Notes." else "The launcher appears as VAANI and opens the safety dashboard.")
            Action("Lock to Notes now", Icons.Outlined.VisibilityOff, disguise, primary = false)
        } }
        item { Heading("LANGUAGE & DISPATCH") }
        item { Panel { Heading("Emergency dispatch"); Caption("Trusted-contact SMS is active when configured. Police, NGO, peer, and shelter panels are preview actions until an approved integration is connected."); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(ShelterAmber)); Spacer(Modifier.width(8.dp)); Text("Preview action") }; TextButton(onClick = { info("Preview: no police or NGO alert was sent.") }) { Text("About preview actions") } } }
        item { Action("Test interface quietly", Icons.Outlined.CheckCircleOutline, { info("Interface check complete. No recording was started and no contacts were notified. To test real recording, use the activation hold on Spaces.") }) }
    }
}
