package com.zut.smartstudy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM saved_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedNote>>

    @Insert
    suspend fun insert(note: SavedNote): Long

    @Query("UPDATE saved_notes SET revised = :revised WHERE id = :id")
    suspend fun setRevised(id: Long, revised: Boolean)

    @Delete
    suspend fun delete(note: SavedNote)
}
