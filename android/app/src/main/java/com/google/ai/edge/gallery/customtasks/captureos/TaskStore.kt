package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/** One to-do the on-device model pulled out of a recording or a typed note. */
data class CapturedTask(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val owner: String = "",
  val dueDate: String = "", // yyyy-MM-dd or ""
  val dueTime: String = "", // HH:mm or ""
  val priority: String = "medium",
  val source: String = "", // "meeting" | "note"
  val createdAt: Long = System.currentTimeMillis(),
  var done: Boolean = false,
  var reminderAt: Long = 0L, // epoch millis of the scheduled notification, 0 = none
)

/** Tasks kept on the phone only (no server): a small JSON file in shared preferences. */
object TaskStore {
  private const val PREFS = "captureos_tasks"
  private const val KEY = "tasks"
  private val gson = Gson()

  /** Observable list for Compose screens. */
  val tasks = mutableStateListOf<CapturedTask>()
  private var loaded = false

  fun load(context: Context) {
    if (loaded) return
    val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    if (json != null) {
      val type = object : TypeToken<List<CapturedTask>>() {}.type
      tasks.clear()
      tasks.addAll(gson.fromJson<List<CapturedTask>>(json, type))
    }
    loaded = true
  }

  private fun save(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, gson.toJson(tasks.toList())).apply()
  }

  fun add(context: Context, task: CapturedTask): CapturedTask {
    load(context)
    tasks.add(0, task)
    save(context)
    return task
  }

  fun update(context: Context, task: CapturedTask) {
    val i = tasks.indexOfFirst { it.id == task.id }
    if (i >= 0) tasks[i] = task
    save(context)
  }

  fun setDone(context: Context, id: String, done: Boolean) {
    val i = tasks.indexOfFirst { it.id == id }
    if (i < 0) return
    tasks[i] = tasks[i].copy(done = done)
    if (done) ReminderScheduler.cancel(context, id)
    save(context)
  }

  fun delete(context: Context, id: String) {
    tasks.removeAll { it.id == id }
    ReminderScheduler.cancel(context, id)
    save(context)
  }
}
