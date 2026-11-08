@file:OptIn(ExperimentalMaterial3Api::class)

package com.zut.smartstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain"
)

@Composable
fun PickerScreen(vm: StudyViewModel, onBack: () -> Unit, onGenerated: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.onDocumentPicked(uri)
    }
    val useAi = vm.generationMode == GenerationMode.AI

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (useAi) "Generate with AI" else "Document & topic") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (useAi) "AI generation starts with your document's extracted key points, then Gemini improves their clarity and removes repetition."
                else "Generate key points on your device. This option works offline.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(onClick = { launcher.launch(MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.pickedUri == null) "Select PDF / Word / text document" else "Change document")
            }
            if (vm.pickedUri != null) {
                Text("Selected: ${vm.pickedName}", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = vm.topic,
                onValueChange = { vm.topic = it },
                label = { Text("Topic (e.g. software testing)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (useAi) {
                OutlinedTextField(
                    value = vm.apiKey,
                    onValueChange = { vm.apiKey = it },
                    label = { Text("Gemini API key") },
                    placeholder = { Text("Paste your API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Your key is kept in memory for this app session and is not saved in the notes database. For a public release, use a secure backend rather than embedding a personal API key in the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    if (useAi) vm.generateWithAi(onGenerated) else vm.generate(onGenerated)
                },
                enabled = vm.pickedUri != null && vm.topic.isNotBlank() &&
                    (!useAi || vm.apiKey.isNotBlank()) && !vm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (useAi) "Generate key points using AI" else "Generate key points")
            }

            if (vm.loading) CircularProgressIndicator()
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
