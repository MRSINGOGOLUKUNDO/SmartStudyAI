
@file:OptIn(ExperimentalMaterial3Api::class)

package com.zut.smartstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zut.smartstudy.ui.components.ShineButton
import com.zut.smartstudy.ui.components.StudyBackground
import com.zut.smartstudy.ui.theme.SoftWhite

private val MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain"
)

@Composable
fun PickerScreen(
    vm: StudyViewModel,
    onBack: () -> Unit,
    onGenerated: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        vm.onDocumentPicked(uri)
    }

    val isGemini = vm.generationMode == GenerationMode.AI
    val isGroq = vm.generationMode == GenerationMode.GROQ
    val isOffline = vm.generationMode == GenerationMode.OFFLINE

    val title = when (vm.generationMode) {
        GenerationMode.OFFLINE -> "Offline generation"
        GenerationMode.AI -> "Generate with Gemini"
        GenerationMode.GROQ -> "Generate with Groq"
    }

    val description = when (vm.generationMode) {
        GenerationMode.OFFLINE ->
            "Generate meaningful study key points directly on your device. No internet is required."

        GenerationMode.AI ->
            "Your document is processed offline first. Gemini then improves the clarity and organisation of the key points."

        GenerationMode.GROQ ->
            "Your document is processed offline first. Groq then improves the clarity and organisation of the key points."
    }

    val apiKeyReady = when (vm.generationMode) {
        GenerationMode.OFFLINE -> true
        GenerationMode.AI -> vm.apiKey.isNotBlank()
        GenerationMode.GROQ -> vm.groqApiKey.isNotBlank()
    }

    StudyBackground {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = SoftWhite,
                    navigationIconContentColor = SoftWhite
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                description,
                style = MaterialTheme.typography.bodyMedium
            )

            ShineButton(
                text = if (vm.pickedUri == null) {
                    "Select PDF / Word / text document"
                } else {
                    "Change document"
                },
                onClick = { launcher.launch(MIME_TYPES) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.loading,
                outlined = true
            )

            if (vm.pickedUri != null) {
                Text(
                    "Selected: ${vm.pickedName}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedTextField(
                value = vm.topic,
                onValueChange = { vm.topic = it },
                label = { Text("Topic (e.g. software testing)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.loading
            )

            if (isGemini) {
                OutlinedTextField(
                    value = vm.apiKey,
                    onValueChange = { vm.apiKey = it },
                    label = { Text("Gemini API key") },
                    placeholder = { Text("Paste your Gemini API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.loading
                )
            }

            if (isGroq) {
                OutlinedTextField(
                    value = vm.groqApiKey,
                    onValueChange = { vm.groqApiKey = it },
                    label = { Text("Groq API key") },
                    placeholder = { Text("Paste your Groq API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.loading
                )
            }

            if (isGemini || isGroq) {
                Text(
                    "AI services require an internet connection. If AI fails, " +
                            "the app will use the offline-generated points instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ShineButton(
                text = when {
                    isOffline -> "Generate key points"
                    isGemini -> "Generate key points using Gemini"
                    isGroq -> "Generate key points using Groq"
                    else -> "Generate key points"
                },
                onClick = {
                    vm.generateForSelectedMode(onGenerated)
                },
                enabled = vm.pickedUri != null &&
                        vm.topic.isNotBlank() &&
                        apiKeyReady &&
                        !vm.loading,
                modifier = Modifier.fillMaxWidth()
            )

            if (vm.loading) {
                CircularProgressIndicator()
                Text("Processing your document. Please wait.")
            }

            vm.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    }
}
