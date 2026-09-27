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
import java.util.concurrent.TimeUnit

private const val CHANNEL_EVENTS = "captureos_events"
private const val KEY_EVENT_ID = "event_id"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"
private const val KEY_STAGE = "stage"

/**
 * Progressive reminders: one notification per stage before an event starts
 * (24 h, 2 h, 30 min, 5 min). WorkManager work is persisted, so it survives app close and reboot.
 */
object ReminderChain {

  private val STAGES =
    listOf(
      "T-24h" to 24L * 60 * 60 * 1000,
      "T-2h" to 2L * 60 * 60 * 1000,
      "T-30m" to 30L * 60 * 1000,
      "T-5m" to 5L * 60 * 1000,
    )

  fun schedule(context: Context, event: CalendarEvent) {
    if (event.startAt <= 0L) return
    val now = System.currentTimeMillis()
    for ((stage, lead) in STAGES) {
      val fireAt = event.startAt - lead
      if (fireAt <= now) continue
      if (event.firedStages.contains(stage)) continue
      val body = leadText(lead) + if (event.owner.isNotBlank()) " · ${event.owner}" else ""
      val request =
        OneTimeWorkRequestBuilder<EventReminderWorker>()
          .setInitialDelay(fireAt - now, TimeUnit.MILLISECONDS)
          .setInputData(
            workDataOf(
              KEY_EVENT_ID to event.id,
              KEY_TITLE to event.title,
              KEY_BODY to body,
              KEY_STAGE to stage,
            )
          )
          .addTag(event.id)
          .build()
      WorkManager.getInstance(context).enqueue(request)
    }
  }

  fun cancel(context: Context, eventId: String) {
    WorkManager.getInstance(context).cancelAllWorkByTag(eventId)
  }

  private fun leadText(lead: Long): String =
    when {
      lead >= 24L * 60 * 60 * 1000 -> "Tomorrow"
      lead >= 60L * 60 * 1000 -> "In ${lead / (60 * 60 * 1000)} hours"
      else -> "In ${lead / (60 * 1000)} minutes"
    }

  fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_EVENTS) == null) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_EVENTS, "Deadlines and meetings", NotificationManager.IMPORTANCE_HIGH).apply {
          description = "Progressing reminders before a deadline or meeting"
        }
      )
    }
  }
}

class EventReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  override fun doWork(): Result {
    val ctx = applicationContext
    if (
      ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return Result.success()
    }
    ReminderChain.ensureChannel(ctx)
    val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.success()
    val stage = inputData.getString(KEY_STAGE) ?: ""
    val title = inputData.getString(KEY_TITLE) ?: "Reminder"
    val body = inputData.getString(KEY_BODY) ?: ""

    CaptureDb.load(ctx)
    val event = CaptureDb.state.events.firstOrNull { it.id == eventId }
    if (event == null || event.status == "done") return Result.success()
    if (!event.firedStages.contains(stage)) {
      CaptureDb.updateEvent(ctx, event.copy(firedStages = event.firedStages + stage))
    }

    val open =
      PendingIntent.getActivity(
        ctx,
        eventId.hashCode(),
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(ctx, CHANNEL_EVENTS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("$body · $title")
        .setContentText("${event.kind} · ${event.priority} priority")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(open)
        .build()
    NotificationManagerCompat.from(ctx).notify((eventId + stage).hashCode(), notification)
    return Result.success()
  }
}
