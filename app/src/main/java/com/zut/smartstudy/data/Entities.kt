package com.zut.smartstudy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeatDaily: Boolean,
    val enabled: Boolean = true
)

@Entity(tableName = "saved_notes")
data class SavedNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val documentName: String,
    /** Key points joined with '\n' (one point per line). */
    val points: String,
    val revised: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
