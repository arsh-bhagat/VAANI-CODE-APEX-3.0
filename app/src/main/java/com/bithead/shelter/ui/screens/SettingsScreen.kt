package com.bithead.shelter.ui.screens

import com.bithead.shelter.ui.localization.uiText
import com.bithead.shelter.ui.localization.tr

import androidx.compose.material3.Text
import android.content.Intent
import android.location.Location
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.bithead.shelter.R
import com.bithead.shelter.ui.walkthrough.*
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
    onRequestSmsPermission: () -> Unit, onImportMedia: () -> Unit, onCaptureMedia: () -> Unit,
    replay: () -> Unit, featureHelp: () -> Unit, tourEnabled: Boolean, speechStatus: String,
    onUseSuggestedSafeword: (String) -> Unit, onRefreshSpeech: () -> Unit, onDownloadSpeech: () -> Unit) {
    val context = LocalContext.current
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactNumber by rememberSaveable { mutableStateOf("") }
    LazyColumn(modifier = Modifier.testTag("settings_list"), state = LocalWalkthrough.current?.lists?.get(4) ?: rememberLazyListState(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "ui_language") { UiLanguageSettingsCard(tourEnabled) }
        item(key = "google_account") { GoogleAccountPanel() }
        item(key = "language") { SpeechSettingsCard(safeword, speechStatus, onUseSuggestedSafeword,
            onRefreshSpeech, onDownloadSpeech, tourEnabled) }
        item(key = "help") { Panel(Modifier.featureTarget(FeatureHelp.REPLAY)) {
            Heading(stringResource(R.string.tour_help))
            Button(onClick = replay, enabled = tourEnabled) { Text(stringResource(R.string.tour_replay)) }
            TextButton(onClick = featureHelp) { Text(stringResource(R.string.tour_feature_help)) }
            if (!tourEnabled) Caption(stringResource(R.string.tour_wait))
        } }
        item { Panel { Heading("Protection preferences"); Caption("Local capture • notes disguise"); Badge(if (listening || gestureWakeMode) "Enabled" else "Paused") } }
        item { Heading("ACOUSTIC TRIGGER") }
        item { Panel(color = ShelterSurfaceRaised) {
            Caption("Current safeword phrase")
            Heading("“$safeword”")
            Action("Edit safeword", Icons.Outlined.Edit, edit, primary = false)
            Row(verticalAlignment = Alignment.CenterVertically) { Text(uiText("Continuous acoustic trigger"), Modifier.weight(1f)); BrutalSwitch(listening, onListen, label = "Continuous acoustic trigger") }
            Caption("Turning this off stops continuous listening. Motion confirmation stays independently armed and can still listen for your safeword for 15 seconds.")
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Motion confirmation (15s window)"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                BrutalSwitch(gestureWakeMode, onGestureWakeModeChange, label = "Motion confirmation")
            }
            Caption("Uses accelerometer and gyroscope rules; no trained model is needed. A possible drop or snatch opens a 15-second safeword window while VAANI or Notes is open in the foreground. No safeword means no alert.")
            Caption("Motion rules can miss events or respond to ordinary handling. Test on your device before relying on this feature.")
            Caption("Offline recognition depends on your device.")
            FeatureHelpLink(FeatureHelp.GESTURE)
            FeatureHelpLink(FeatureHelp.SAFEWORD)
        } }
        item { Heading("DEVICE & VAULT SECURITY") }
        item { Panel { Heading("Biometric vault lock"); FeatureHelpLink(FeatureHelp.SECURITY); Caption("Uses your device fingerprint, face, or screen lock. The vault locks when the app leaves the foreground."); Action("Device security settings", Icons.Outlined.Fingerprint, { context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }, primary = false) } }
        item { Panel { Heading("Location protection"); FeatureHelpLink(FeatureHelp.LOCATION); Caption("Keeps a timestamped last known location ready for emergency SMS. Android shows an ongoing notification while active."); Row(verticalAlignment = Alignment.CenterVertically) { Text(uiText("Track while armed"), Modifier.weight(1f)); BrutalSwitch(locationTracking, onLocationTrackingChange, label = "Location tracking") }; Caption(if (cachedLocation == null) "No location fix cached yet" else "Last fix: ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, context.resources.configuration.locales[0]).format(Date(cachedLocation.timestampMillis))}") } }
        item(key = "contacts") {
            Panel {
                Heading("Trusted contacts")
                FeatureHelpLink(FeatureHelp.CONTACTS)
                Caption("Emergency SMS sends only to contacts you add here.")
                trustedContacts.forEach { contact ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(contact.name); Caption(contact.number) }
                        TextButton(onClick = { onRemoveContact(contact.number) }) { Text(uiText("Remove")) }
                    }
                }
                Column(Modifier.fillMaxWidth().featureTarget(FeatureHelp.CONTACTS),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(contactName, { contactName = it }, Modifier.fillMaxWidth(),
                        label = { Text(uiText("Name")) }, singleLine = true)
                    OutlinedTextField(contactNumber, { contactNumber = it }, Modifier.fillMaxWidth(),
                        label = { Text(uiText("Phone number")) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onAddContact(contactName, contactNumber)
                            contactName = ""; contactNumber = ""
                        }) { Text(uiText("Add")) }
                        OutlinedButton(onClick = onPickContact) { Text(uiText("Pick contact")) }
                    }
                    OutlinedButton(onClick = onRequestSmsPermission, modifier = Modifier.fillMaxWidth()) {
                        Text(uiText("Allow emergency SMS"))
                    }
                }
                smsStatuses.forEach { status -> Row { Text(status.name); Caption(": ${tr(status.status)}") } }
            }
        }
        item { Panel { Heading("Camera evidence"); FeatureHelpLink(FeatureHelp.CAMERA); Caption("Capture private photos or silent videos up to 60 seconds, then seal them in the encrypted vault."); Action("Open private camera", Icons.Outlined.PhotoCamera, onCaptureMedia); Action("Import image or video", Icons.Outlined.FileOpen, onImportMedia, primary = false) } }
        item { Heading("DECOY APP PERSONA") }
        item(key = "disguise") { Panel(Modifier.featureTarget(FeatureHelp.DISGUISE), color = ShelterSurfaceRaised) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Disguise as Notes App"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                BrutalSwitch(disguiseEnabled, onDisguiseEnabledChange, label = "Notes launcher disguise")
            }
            Caption(if (disguiseEnabled) "The launcher appears as Notes and future launches open All Notes." else "The launcher appears as VAANI and opens the safety dashboard.")
            FeatureHelpLink(FeatureHelp.DISGUISE)
            Action("Lock to Notes now", Icons.Outlined.VisibilityOff, disguise, primary = false)
        } }
        item { Heading("LANGUAGE & DISPATCH") }
        item { Panel { Heading("Emergency dispatch"); FeatureHelpLink(FeatureHelp.DISPATCH); Caption("Trusted-contact SMS is active when configured. Police, NGO, peer, and shelter panels are preview actions until an approved integration is connected."); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(ShelterAmber)); Spacer(Modifier.width(8.dp)); Text(uiText("Preview action")) }; TextButton(onClick = { info("Preview: no police or NGO alert was sent.") }) { Text(uiText("About preview actions")) } } }
        item { Action("Test interface quietly", Icons.Outlined.CheckCircleOutline, { info("Interface check complete. No recording was started and no contacts were notified. To test real recording, use the activation hold on Vaani.") }) }
    }
}
