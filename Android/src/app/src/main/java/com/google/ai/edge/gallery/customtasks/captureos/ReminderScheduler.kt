package com.google.ai.edge.gallery.customtasks.captureos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "captureos_reminders"
private const val KEY_ID = "task_id"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"

/** Task reminders as local notifications (WorkManager, survives app close and reboot). */
object ReminderScheduler {

  /**
   * When to remind: the due time if one was said, else 9:00 on the due date. If that moment has
   * already passed today, remind in one minute; past dates get no reminder. Returns epoch millis or 0.
   */
  fun reminderTime(task: CapturedTask, now: LocalDateTime = LocalDateTime.now()): Long {
    val date = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: return 0L
    val time = runCatching { LocalTime.parse(task.dueTime) }.getOrNull() ?: LocalTime.of(9, 0)
    var at = LocalDateTime.of(date, time)
    if (at.isBefore(now)) {
      if (date != now.toLocalDate()) return 0L
      at = now.plusMinutes(1)
    }
    return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
  }

  fun schedule(context: Context, task: CapturedTask): Long {
    val at = reminderTime(task)
    if (at == 0L) return 0L
    val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
    val body = buildString {
      if (task.owner.isNotBlank()) append("${task.owner} · ")
      append("${task.priority} priority")
      if (task.dueTime.isNotBlank()) append(" · due ${task.dueTime}")
    }
    val request =
      OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf(KEY_ID to task.id, KEY_TITLE to task.title, KEY_BODY to body))
        .addTag(task.id)
        .build()
    WorkManager.getInstance(context).enqueue(request)
    return at
  }

  fun cancel(context: Context, taskId: String) {
    WorkManager.getInstance(context).cancelAllWorkByTag(taskId)
  }

  fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
          description = "Reminders for tasks CaptureOS found in your meetings and notes"
        }
      )
    }
  }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  override fun doWork(): Result {
    val ctx = applicationContext
    if (
      ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return Result.success()
    }
    ReminderScheduler.ensureChannel(ctx)
    val id = inputData.getString(KEY_ID) ?: return Result.success()
    val open =
      PendingIntent.getActivity(
        ctx,
        id.hashCode(),
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("⏰ ${inputData.getString(KEY_TITLE)}")
        .setContentText(inputData.getString(KEY_BODY))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(open)
        .build()
    NotificationManagerCompat.from(ctx).notify(id.hashCode(), notification)
    return Result.success()
  }
}
