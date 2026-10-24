package com.bithead.shelter.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.bithead.shelter.R
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.ui.theme.*


@Composable
internal fun NotesScreen(safeword: String, unlock: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val prefs = remember { context.getSharedPreferences("vaani_notes", 0) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All Notes") }
    var note by rememberSaveable { mutableStateOf(prefs.getString("memo", "") ?: "") }
    var editor by remember { mutableStateOf(false) }
    val groceries = listOf("Cold-pressed olive oil & balsamic vinegar", "Organic unsweetened almond milk", "Artisan sourdough boule (sliced)", "Fresh mint leaves & baby spinach", "Loose leaf chamomile tea")
    val checked = remember { mutableStateListOf(*Array(5) { prefs.getBoolean("grocery_$it", it < 2) }) }
    fun matches(title: String, group: String) = (category == "All Notes" || category == group) && title.contains(query, true)
    fun unlockIfMatched(value: String): Boolean {
        if (!matchesNotesUnlock(value, safeword)) return false
        query = ""
        focusManager.clearFocus()
        keyboard?.hide()
        unlock()
        return true
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { OutlinedTextField(query, { value ->
            if (!unlockIfMatched(value)) query = value
        }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.notes_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(4.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { unlockIfMatched(query) })) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All Notes", "Groceries", "Reading", "Work Sync").forEach { label -> FilterChip(category == label, { category = label }, label = { Text(label) }) } } }
        if (matches("Weekly Grocery & Market", "Groceries")) item { Panel {
            Heading("Weekly Grocery & Market"); Caption("Items for Saturday brunch prep & weekly staples from the corner market.")
            groceries.forEachIndexed { i, text -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked[i], { checked[i] = it; prefs.edit().putBoolean("grocery_$i", it).apply() }); Text(text, style = MaterialTheme.typography.bodyMedium, textDecoration = if (checked[i]) TextDecoration.LineThrough else null) } }
            Caption("${checked.count { it }} of 5 done  •  Groceries")
        } }
        if (matches("Reading Journal", "Reading")) item { Panel { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(32.dp), ShelterSafe); Heading("Reading Journal"); Caption("‘In Praise of Shadows’ & essays on quiet architecture, domestic rituals…"); Badge("Personal • 3 recommendations") } }
        if (matches("Tuesday Sync: Platform Review", "Work Sync")) item { Panel { Heading("Tuesday Sync: Platform Review"); Caption("Action items from product design alignment and quarterly review:"); Text("• Standardize typography across the app.\n\n• Review the memo input experience.\n\n• Finalize this week's deliverables."); Caption("Updated by Sarah • Work Sync") } }
        if (matches("Roasted Butternut Soup", "Groceries")) item { Panel { Heading("Roasted Butternut Soup"); Caption("Caramelize with nutmeg, brown butter, shallots, and crisp sage…"); Badge("Kitchen • 45 mins") } }
        if (note.isNotBlank() && matches(note, "All Notes")) item { Panel { Heading("My memo"); Text(note); TextButton(onClick = { editor = true }) { Text("Edit memo") } } }
        item { Action("Write a memo", Icons.Outlined.EditNote, { editor = true }) }
    }
    if (editor) AlertDialog(onDismissRequest = { editor = false }, title = { Text("My memo") }, text = { OutlinedTextField(note, { note = it; prefs.edit().putString("memo", it).apply() }, minLines = 4, label = { Text("Note") }) },
        confirmButton = { TextButton(onClick = { prefs.edit().putString("memo", note).apply(); editor = false }) { Text("Save") } })
}
