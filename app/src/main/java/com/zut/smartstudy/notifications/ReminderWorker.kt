package com.zut.smartstudy.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zut.smartstudy.data.AppDatabase

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(ReminderScheduler.KEY_ID, -1L)
        if (id < 0) return Result.failure()

        val dao = AppDatabase.get(applicationContext).reminderDao()
        val reminder = dao.get(id) ?: return Result.success()
        if (!reminder.enabled) return Result.success()

        NotificationHelper.show(applicationContext, reminder.id, reminder.title)

        if (reminder.repeatDaily) {
            ReminderScheduler.schedule(applicationContext, reminder, fromWorker = true)
        } else {
            dao.update(reminder.copy(enabled = false)) // one-off reminder is done
        }
        return Result.success()
    }
}
