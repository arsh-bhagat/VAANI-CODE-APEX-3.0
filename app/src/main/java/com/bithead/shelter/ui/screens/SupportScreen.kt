package com.bithead.shelter.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.bithead.shelter.ui.walkthrough.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.theme.*

@Composable
internal fun SupportScreen(disguise: () -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf("") }
    LazyColumn(state = LocalWalkthrough.current?.lists?.get(3) ?: rememberLazyListState(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "support") { BrutalPanel(Modifier.featureTarget(FeatureHelp.SUPPORT), header = "01 / Crisis & legal support") {
            Icon(Icons.Outlined.HealthAndSafety, null, Modifier.size(40.dp), GoldPrimary)
            Heading("Support when you need it")
            FeatureHelpLink(FeatureHelp.SUPPORT)
            Caption("Public crisis and legal resources. Calls open your dialer so you remain in control.")
            StatusPill("Direct dial / India", tone = StatusTone.Mesh)
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) {
                Icon(Icons.Outlined.LocalPolice, null, tint = ShelterBlueSoft)
                Heading("Nearest station")
                Caption("Search public stations in device maps")
                Action("Locate", Icons.Outlined.NearMe, { openLink(context, "geo:0,0?q=nearby+police+station") }, primary = false)
            }
            Panel(Modifier.weight(1f)) {
                Icon(Icons.Outlined.VolunteerActivism, null, tint = GoldPrimary)
                Heading("Local care")
                Caption("Search nearby public hospitals")
                Action("Find care", Icons.Outlined.NearMe, { openLink(context, "geo:0,0?q=nearby+hospital") }, primary = false)
            }
        } }
        item { Heading("EMERGENCY & SUPPORT • INDIA") }
        item { Panel(color = ShelterSurfaceRaised) { Icon(Icons.Outlined.HealthAndSafety, null, tint = ShelterSafe); Heading("Emergency assistance"); Caption("India's emergency response number for police, fire and medical emergencies."); Action("Dial 112", Icons.Outlined.Phone, { openLink(context, "tel:112") }); TextButton(onClick = { openLink(context, "https://112.gov.in/") }) { Text("Official service information") } } }
        item { Panel { Heading("Legal Aid & Assistance"); Caption("National Legal Services Authority (NALSA)"); Badge("Public helpline"); Action("Dial 15100", Icons.Outlined.Gavel, { openLink(context, "tel:15100") }, primary = false); TextButton(onClick = { openLink(context, "https://nalsa.gov.in/womens-assistance/") }) { Text("Legal assistance resources") } } }
        item { Panel { Heading("Cyber Safety & Harassment"); Caption("Use the National Cyber Crime Reporting Portal to report cybercrime. 1930 is the financial cyber fraud helpline."); Action("Open reporting portal", Icons.Outlined.Security, { openLink(context, "https://cybercrime.gov.in/") }, primary = false); TextButton(onClick = { openLink(context, "tel:1930") }) { Text("Dial 1930 • financial fraud") } } }
        item { Heading("SUPPORT GUIDES") }
        listOf("Finding legal assistance" to "Explore NALSA's official assistance page for available services and eligibility.", "Digital privacy" to "Review app permissions and linked devices in your device settings. The official cybercrime portal provides reporting resources.", "Finding local care" to "Use device maps to search for nearby hospitals. In an emergency in India, open the dialer for 112.").forEach { (title, text) ->
            item { Panel(color = ShelterSurfaceRaised) { TextButton(onClick = { expanded = if (expanded == title) "" else title }) { Text(title, Modifier.weight(1f)); Icon(if (expanded == title) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }; if (expanded == title) Caption(text) } }
        }
        item { Action("Quick exit to notes", Icons.Outlined.VisibilityOff, disguise, primary = false) }
    }
}
