@file:OptIn(ExperimentalMaterial3Api::class)

package com.zut.smartstudy.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zut.smartstudy.data.Reminder
import com.zut.smartstudy.data.SavedNote
import com.zut.smartstudy.ui.components.ShineButton
import com.zut.smartstudy.ui.components.StudyBackground
import com.zut.smartstudy.ui.components.softPress
import com.zut.smartstudy.ui.components.studyCardColors
import com.zut.smartstudy.ui.theme.ButtonBlue
import com.zut.smartstudy.ui.theme.SoftWhite
import com.zut.smartstudy.ui.theme.SurfaceNavy

@Composable
fun DashboardScreen(
    vm: StudyViewModel,
    onPickDocument: (GenerationMode) -> Unit,
    onOpenNote: () -> Unit
) {
    val context = LocalContext.current
    val reminders by vm.reminders.collectAsState()
    val notes by vm.notes.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val fabSource = remember { MutableInteractionSource() }

    StudyBackground(showPhotos = true) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("SmartStudy AI") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = SoftWhite
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                modifier = Modifier.softPress(fabSource),
                containerColor = ButtonBlue,
                contentColor = SoftWhite,
                interactionSource = fabSource,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New reminder") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = studyCardColors()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Summarise your notes", style = MaterialTheme.typography.titleMedium)
                        Text("Choose how you want to create your revision notes.")
                        ShineButton(
                            text = "Generate key points",
                            onClick = { onPickDocument(GenerationMode.OFFLINE) },
                            modifier = Modifier.fillMaxWidth(),
                            shine = false
                        )
                        ShineButton(
                            text = "Generate key points using AI",
                            onClick = { onPickDocument(GenerationMode.AI) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ShineButton(
                            text = "Generate key points using AI (2)",
                            onClick = { onPickDocument(GenerationMode.GROQ) },
                            modifier = Modifier.fillMaxWidth(),
                            shineDelayMs = 2500
                        )
                        Text(
                            "Offline generation works without internet. AI uses Gemini and AI (2) uses Groq to refine the extracted points.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Text("Study reminders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (reminders.isEmpty()) {
                item { Text("No reminders yet. Tap “New reminder” to add one.") }
            }
            items(reminders, key = { "r${it.id}" }) { r ->
                ReminderRow(
                    reminder = r,
                    onToggle = { vm.setReminderEnabled(r, it) },
                    onDelete = { vm.deleteReminder(r) }
                )
            }

            item { Text("Saved notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (notes.isEmpty()) {
                item { Text("Nothing saved yet.") }
            }
            items(notes, key = { "n${it.id}" }) { n ->
                NoteRow(
                    note = n,
                    onClick = { vm.openNote(n); onOpenNote() },
                    onDelete = { vm.deleteNote(n) }
                )
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
        }
    }
    }

    if (showDialog) {
        AddReminderDialog(
            onDismiss = { showDialog = false },
            onConfirm = { title, h, m, daily ->
                vm.addReminder(title, h, m, daily)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = studyCardColors()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("%02d:%02d".format(reminder.hour, reminder.minute), style = MaterialTheme.typography.titleLarge)
                Text(reminder.title)
                Text(
                    if (reminder.repeatDaily) "Repeats daily" else "One time",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = reminder.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete reminder") }
        }
    }
}

@Composable
private fun NoteRow(note: SavedNote, onClick: () -> Unit, onDelete: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().softPress(source),
        colors = studyCardColors(),
        interactionSource = source
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(note.topic, style = MaterialTheme.typography.titleMedium)
                Text(note.documentName, style = MaterialTheme.typography.bodySmall)
                if (note.revised) {
                    Text("✓ Revised", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete note") }
        }
    }
}

@Composable
private fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, hour: Int, minute: Int, daily: Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(18) }
    var minute by remember { mutableStateOf(0) }
    var daily by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceNavy,
        titleContentColor = SoftWhite,
        textContentColor = SoftWhite,
        title = { Text("New study reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What are you revising?") },
                    singleLine = true
                )
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }) { Text("Time: %02d:%02d".format(hour, minute)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = daily, onCheckedChange = { daily = it })
                    Text("Repeat every day")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(title, hour, minute, daily) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
