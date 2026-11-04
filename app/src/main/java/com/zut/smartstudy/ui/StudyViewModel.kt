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
    val revised: Boolean = false
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StudyRepository(AppDatabase.get(app))

    val reminders: StateFlow<List<Reminder>> =
        repo.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notes: StateFlow<List<SavedNote>> =
        repo.notes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Picker state ----
    var pickedUri by mutableStateOf<Uri?>(null); private set
    var pickedName by mutableStateOf(""); private set
    var topic by mutableStateOf("")
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    // ---- Summary state ----
    var current by mutableStateOf<SummaryUi?>(null); private set

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        pickedUri = uri
        pickedName = TextExtractor.displayName(getApplication(), uri)
        error = null
    }

    fun generate(onDone: () -> Unit) {
        val uri = pickedUri ?: return
        val t = topic.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val text = withContext(Dispatchers.IO) { TextExtractor.extract(getApplication(), uri) }
                val points = withContext(Dispatchers.Default) { KeyPointGenerator.generate(text, t) }
                if (points.isEmpty()) {
                    error = "No content about \"$t\" was found in this document. Try a different keyword."
                } else {
                    current = SummaryUi(topic = t, documentName = pickedName, points = points)
                    onDone()
                }
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong while reading the document."
            } finally {
                loading = false
            }
        }
    }

    // ---- Notes ----
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
        val c = current ?: return@launch
        if (c.savedId != null) return@launch
        val id = repo.addNote(c.toEntity())
        current = c.copy(savedId = id)
    }

    fun toggleRevised() = viewModelScope.launch {
        val c = current ?: return@launch
        val newValue = !c.revised
        if (c.savedId == null) {
            val id = repo.addNote(c.copy(revised = newValue).toEntity())
            current = c.copy(savedId = id, revised = newValue)
        } else {
            repo.setRevised(c.savedId, newValue)
            current = c.copy(revised = newValue)
        }
    }

    fun deleteNote(note: SavedNote) = viewModelScope.launch {
        repo.deleteNote(note)
        if (current?.savedId == note.id) current = current?.copy(savedId = null)
    }

    private fun SummaryUi.toEntity() = SavedNote(
        topic = topic, documentName = documentName, points = points.joinToString("\n"), revised = revised
    )

    // ---- Reminders ----
    fun addReminder(title: String, hour: Int, minute: Int, repeatDaily: Boolean) = viewModelScope.launch {
        val draft = Reminder(title = title.ifBlank { "Study session" }, hour = hour, minute = minute, repeatDaily = repeatDaily)
        val id = repo.addReminder(draft)
        ReminderScheduler.schedule(getApplication(), draft.copy(id = id))
    }

    fun setReminderEnabled(r: Reminder, enabled: Boolean) = viewModelScope.launch {
        val updated = r.copy(enabled = enabled)
        repo.updateReminder(updated)
        if (enabled) ReminderScheduler.schedule(getApplication(), updated)
        else ReminderScheduler.cancel(getApplication(), r.id)
    }

    fun deleteReminder(r: Reminder) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication(), r.id)
        repo.deleteReminder(r)
    }
}
