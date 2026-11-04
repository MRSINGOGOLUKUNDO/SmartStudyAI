package com.zut.smartstudy.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zut.smartstudy.data.Reminder
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules study reminders with WorkManager so they fire even when the app is closed
 * (and survive device reboots). Each reminder is a unique one-time job; daily reminders
 * re-schedule themselves from [ReminderWorker].
 */
object ReminderScheduler {
    const val KEY_ID = "reminder_id"

    private fun workName(id: Long) = "reminder_$id"

    /** Milliseconds from [from] until the next occurrence of hour:minute. */
    fun delayUntilNext(hour: Int, minute: Int, from: Calendar = Calendar.getInstance()): Long {
        val next = (from.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(from)) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis - from.timeInMillis
    }

    fun schedule(context: Context, reminder: Reminder, fromWorker: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNext(reminder.hour, reminder.minute), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_ID to reminder.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(reminder.id),
            if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, reminderId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(reminderId))
    }
}
