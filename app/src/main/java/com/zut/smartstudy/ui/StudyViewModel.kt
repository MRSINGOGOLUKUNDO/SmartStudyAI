package com.zut.smartstudy.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zut.smartstudy.data.AppDatabase
import com.zut.smartstudy.data.Reminder
import com.zut.smartstudy.data.SavedNote
import com.zut.smartstudy.data.StudyRepository
import com.zut.smartstudy.domain.AiKeyPointGenerator
import com.zut.smartstudy.domain.KeyPointGenerator
import com.zut.smartstudy.domain.TextExtractor
import com.zut.smartstudy.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SummaryUi(
    val topic: String,
    val documentName: String,
    val points: List<String>,
    val savedId: Long? = null,
    val revised: Boolean = false,
    val generatedWithAi: Boolean = false
)

enum class GenerationMode {
    OFFLINE,
    AI
}

class StudyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StudyRepository(AppDatabase.get(app))

    val reminders: StateFlow<List<Reminder>> =
        repo.reminders.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val notes: StateFlow<List<SavedNote>> =
        repo.notes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // Picker state. The API key is held in memory only.
    var pickedUri by mutableStateOf<Uri?>(null)
        private set

    var pickedName by mutableStateOf("")
        private set

    var topic by mutableStateOf("")
    var apiKey by mutableStateOf("")

    var generationMode by mutableStateOf(GenerationMode.OFFLINE)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // Summary state
    var current by mutableStateOf<SummaryUi?>(null)
        private set

    fun selectGenerationMode(mode: GenerationMode) {
        generationMode = mode
        error = null
    }

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return

        pickedUri = uri
        pickedName = TextExtractor.displayName(getApplication(), uri)
        error = null
    }

    /** Uses the original on-device generator; this path works without internet. */
    fun generate(onDone: () -> Unit) {
        generateInternal(useAi = false, onDone = onDone)
    }

    /** Runs the offline generator first, then asks Gemini to refine its draft points. */
    fun generateWithAi(onDone: () -> Unit) {
        if (apiKey.isBlank()) {
            error = "Enter your Gemini API key before generating key points with AI."
            return
        }

        generateInternal(useAi = true, onDone = onDone)
    }

    private fun generateInternal(useAi: Boolean, onDone: () -> Unit) {
        val uri = pickedUri ?: run {
            error = "Select a document first."
            return
        }

        val selectedTopic = topic.trim()
        if (selectedTopic.isEmpty()) {
            error = "Enter a topic first."
            return
        }

        if (loading) return

        // Capture values before starting the coroutine.
        val selectedApiKey = apiKey.trim()
        val selectedDocumentName = pickedName

        viewModelScope.launch {
            loading = true
            error = null

            try {
                val documentText = withContext(Dispatchers.IO) {
                    TextExtractor.extract(getApplication(), uri)
                }

                val draftPoints = withContext(Dispatchers.Default) {
                    KeyPointGenerator.generate(documentText, selectedTopic)
                }

                val finalPoints = if (useAi) {
                    withContext(Dispatchers.IO) {
                        AiKeyPointGenerator.refine(
                            apiKey = selectedApiKey,
                            topic = selectedTopic,
                            draftPoints = draftPoints
                        )
                    }
                } else {
                    draftPoints
                }

                if (finalPoints.isEmpty()) {
                    error =
                        "No useful content about \"$selectedTopic\" was found. " +
                        "Try another topic or document."
                } else {
                    current = SummaryUi(
                        topic = selectedTopic,
                        documentName = selectedDocumentName,
                        points = finalPoints,
                        generatedWithAi = useAi
                    )
                    onDone()
                }
            } catch (e: Exception) {
                error = e.message
                    ?: "Something went wrong while generating key points."
            } finally {
                loading = false
            }
        }
    }

    // Notes
    fun openNote(note: SavedNote) {
        current = SummaryUi(
            topic = note.topic,
            documentName = note.documentName,
            points = note.points.split("\n").filter { it.isNotBlank() },
            savedId = note.id,
            revised = note.revised
        )
    }

    fun saveCurrent() = viewModelScope.launch {
        val summary = current ?: return@launch
        if (summary.savedId != null) return@launch

        val id = repo.addNote(summary.toEntity())
        current = summary.copy(savedId = id)
    }

    fun toggleRevised() = viewModelScope.launch {
        val summary = current ?: return@launch
        val newValue = !summary.revised

        if (summary.savedId == null) {
            val id = repo.addNote(
                summary.copy(revised = newValue).toEntity()
            )
            current = summary.copy(savedId = id, revised = newValue)
        } else {
            repo.setRevised(summary.savedId, newValue)
            current = summary.copy(revised = newValue)
        }
    }

    fun deleteNote(note: SavedNote) = viewModelScope.launch {
        repo.deleteNote(note)

        if (current?.savedId == note.id) {
            current = current?.copy(savedId = null)
        }
    }

    private fun SummaryUi.toEntity() = SavedNote(
        topic = topic,
        documentName = documentName,
        points = points.joinToString("\n"),
        revised = revised
    )

    // Reminders
    fun addReminder(
        title: String,
        hour: Int,
        minute: Int,
        repeatDaily: Boolean
    ) = viewModelScope.launch {
        val draft = Reminder(
            title = title.ifBlank { "Study session" },
            hour = hour,
            minute = minute,
            repeatDaily = repeatDaily
        )

        val id = repo.addReminder(draft)
        ReminderScheduler.schedule(
            getApplication(),
            draft.copy(id = id)
        )
    }

    fun setReminderEnabled(r: Reminder, enabled: Boolean) =
        viewModelScope.launch {
            val updated = r.copy(enabled = enabled)
            repo.updateReminder(updated)

            if (enabled) {
                ReminderScheduler.schedule(getApplication(), updated)
            } else {
                ReminderScheduler.cancel(getApplication(), r.id)
            }
        }

    fun deleteReminder(r: Reminder) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication(), r.id)
        repo.deleteReminder(r)
    }
}
