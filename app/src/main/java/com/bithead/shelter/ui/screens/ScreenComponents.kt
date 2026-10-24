package com.bithead.shelter.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.theme.*
import java.util.Locale

@Composable
internal fun Panel(modifier: Modifier = Modifier, color: Color = ShelterSurface, content: @Composable ColumnScope.() -> Unit) {
    BrutalPanel(modifier = modifier, color = color, content = content)
}

@Composable
internal fun PreviewActionPanel(title: String, detail: String, icon: ImageVector, onOpen: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ShelterSafe)
            Spacer(Modifier.width(10.dp))
            Heading(title)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(9.dp).clip(CircleShape).background(ShelterAmber))
        }
        Caption(detail)
        Action("Open preview", Icons.Outlined.OpenInNew, onOpen, primary = false)
        Text("Preview • no alert was sent", color = ShelterAmber, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun Heading(text: String) { Text(text.uppercase(Locale.ROOT), style = MaterialTheme.typography.titleMedium) }

@Composable
internal fun Caption(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = ShelterTextDim) }

@Composable
internal fun Badge(text: String) { StatusPill(text) }

@Composable
internal fun Action(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    BrutalButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(),
        tone = if (primary) BrutalButtonTone.Primary else BrutalButtonTone.Neutral) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(text)
    }
}
internal fun openLink(context: android.content.Context, uri: String) {
    try { context.startActivity(Intent(if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW, Uri.parse(uri))) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No app available to open this action", Toast.LENGTH_SHORT).show() }
}
