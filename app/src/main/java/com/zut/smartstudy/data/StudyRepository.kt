package com.zut.smartstudy.data

import kotlinx.coroutines.flow.Flow

class StudyRepository(db: AppDatabase) {
    private val reminderDao = db.reminderDao()
    private val noteDao = db.noteDao()

    val reminders: Flow<List<Reminder>> = reminderDao.observeAll()
    val notes: Flow<List<SavedNote>> = noteDao.observeAll()

    suspend fun addReminder(r: Reminder): Long = reminderDao.insert(r)
    suspend fun updateReminder(r: Reminder) = reminderDao.update(r)
    suspend fun deleteReminder(r: Reminder) = reminderDao.delete(r)

    suspend fun addNote(n: SavedNote): Long = noteDao.insert(n)
    suspend fun setRevised(id: Long, revised: Boolean) = noteDao.setRevised(id, revised)
    suspend fun deleteNote(n: SavedNote) = noteDao.delete(n)
}
