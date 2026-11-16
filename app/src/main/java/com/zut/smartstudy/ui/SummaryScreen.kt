@file:OptIn(ExperimentalMaterial3Api::class)

package com.zut.smartstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zut.smartstudy.ui.components.ShineButton
import com.zut.smartstudy.ui.components.StudyBackground
import com.zut.smartstudy.ui.components.studyCardColors
import com.zut.smartstudy.ui.theme.SoftWhite

@Composable
fun SummaryScreen(vm: StudyViewModel, onBack: () -> Unit) {
    val summary = vm.current

    StudyBackground {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(summary?.topic ?: "Key points") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = SoftWhite,
                    navigationIconContentColor = SoftWhite
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (summary != null) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShineButton(
                        text = if (summary.savedId == null) "Save notes" else "Saved ✓",
                        onClick = { vm.saveCurrent() },
                        enabled = summary.savedId == null,
                        shine = false
                    )
                    FilterChip(
                        selected = summary.revised,
                        onClick = { vm.toggleRevised() },
                        label = { Text(if (summary.revised) "Revised ✓" else "Mark as revised") }
                    )
                }
            }
        }
    ) { padding ->
        if (summary == null) {
            Text("Nothing to show.", Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("From: ${summary.documentName}", style = MaterialTheme.typography.bodySmall)
                }
                itemsIndexed(summary.points) { i, point ->
                    Card(Modifier.fillMaxWidth(), colors = studyCardColors()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Column { Text(point, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
        }
    }
    }
}
