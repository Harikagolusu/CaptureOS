package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import java.io.File
import java.util.UUID

/** One Dicto capture: audio and/or a photo, its transcript, its buckets and any tasks. */
data class DictoEntry(
  val id: String = UUID.randomUUID().toString(),
  val createdAt: Long = System.currentTimeMillis(),
  val transcript: String = "",
  val audioPath: String? = null,
  val imagePath: String? = null,
  val categories: List<String> = emptyList(),
  val keyPoints: List<String> = emptyList(),
  val formatted: String = "",
  val tasks: List<CapturedTask> = emptyList(),
)

/** A scheduled thing the AI agent pulled out of a dictation or a meeting. */
data class CalendarEvent(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val kind: String = "task", // meeting | task | deadline
  val startAt: Long = 0L,
  val endAt: Long? = null,
  val priority: String = "medium",
  val owner: String = "",
  val sourceId: String? = null,
  val calendarEventId: Long? = null,
  val status: String = "pending", // pending | done
  val firedStages: List<String> = emptyList(),
)

/** An archived meeting: what was said, what was decided, and its QR. */
data class MeetingArchive(
  val id: String = UUID.randomUUID().toString(),
  val createdAt: Long = System.currentTimeMillis(),
  val teamCode: String,
  val meetingTitle: String,
  val hostName: String = "",
  val status: String = "created", // created | recorded
  val transcript: String = "",
  val summary: String = "",
  val decisions: List<String> = emptyList(),
  val actionItems: List<ActionItem> = emptyList(),
  val qrPngPath: String? = null,
)

/** One line in the local activity log. */
data class ActivityLogEntry(
  val at: Long = System.currentTimeMillis(),
  val type: String,
  val detail: String,
)

data class DbState(
  val dicto: List<DictoEntry> = emptyList(),
  val qrTasks: List<CapturedTask> = emptyList(),
  val events: List<CalendarEvent> = emptyList(),
  val meetings: List<MeetingArchive> = emptyList(),
  val activity: List<ActivityLogEntry> = emptyList(),
)

/** A task plus where it came from: "qr" (scanned check-in) or "dicto" (dictation). */
data class TaskRow(val type: String, val entryId: String?, val task: CapturedTask)

/**
 * Local-only persistence for everything CaptureOS keeps: dictation, calendar events, meeting
 * archives and the activity log. A single JSON file under the app's private files dir, plus
 * binary audio / image / QR files beside it. No network, no accounts.
 */
object CaptureDb {
  private val gson = Gson()
  private const val DIR = "captureos"

  var state by mutableStateOf(DbState())
    private set
  private var loaded = false

  // ---- files -------------------------------------------------------------------------------

  fun root(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

  fun audioDir(context: Context): File = File(root(context), "audio").apply { mkdirs() }

  fun imageDir(context: Context): File = File(root(context), "image").apply { mkdirs() }

  fun qrDir(context: Context): File = File(root(context), "qr").apply { mkdirs() }

  // ---- lifecycle ---------------------------------------------------------------------------

  fun load(context: Context) {
    if (loaded) return
    val file = File(root(context), "db.json")
    if (file.exists()) {
      runCatching {
        val parsed = gson.fromJson(file.readText(), DbState::class.java) ?: DbState()
        state = normalize(parsed)
      }
    }
    loaded = true
  }

  /**
   * Fills in fields that older db.json files do not contain. Gson leaves missing values as null
   * rather than using the Kotlin default, so a record written by an earlier build would otherwise
   * crash the UI on read. Runs once, on load.
   */
  @Suppress("USELESS_ELVIS")
  private fun normalize(s: DbState): DbState =
    DbState(
      dicto =
        (s.dicto ?: emptyList()).map { e ->
          e.copy(
            transcript = e.transcript ?: "",
            categories = e.categories ?: emptyList(),
            keyPoints = e.keyPoints ?: emptyList(),
            formatted = e.formatted ?: "",
            tasks = (e.tasks ?: emptyList()).map { t -> t.copy(sourceId = t.sourceId ?: "") },
          )
        },
      qrTasks = (s.qrTasks ?: emptyList()).map { it.copy(sourceId = it.sourceId ?: "") },
      events = (s.events ?: emptyList()).map { it.copy(firedStages = it.firedStages ?: emptyList()) },
      meetings =
        (s.meetings ?: emptyList()).map { m ->
          m.copy(
            hostName = m.hostName ?: "",
            status = m.status ?: "recorded",
            transcript = m.transcript ?: "",
            summary = m.summary ?: "",
            decisions = m.decisions ?: emptyList(),
            actionItems = m.actionItems ?: emptyList(),
          )
        },
      activity = s.activity ?: emptyList(),
    )

  private fun persist(context: Context) {
    runCatching { File(root(context), "db.json").writeText(gson.toJson(state)) }
  }

  fun log(context: Context, type: String, detail: String) {
    load(context)
    state = state.copy(activity = (listOf(ActivityLogEntry(type = type, detail = detail)) + state.activity).take(500))
    persist(context)
  }

  // ---- dicto -------------------------------------------------------------------------------

  fun addDicto(context: Context, entry: DictoEntry) {
    load(context)
    state = state.copy(dicto = listOf(entry) + state.dicto)
    persist(context)
  }

  fun setTaskDone(context: Context, entryId: String, taskId: String, done: Boolean) {
    load(context)
    state =
      state.copy(
        dicto =
          state.dicto.map { e ->
            if (e.id == entryId) e.copy(tasks = e.tasks.map { t -> if (t.id == taskId) t.copy(done = done) else t })
            else e
          }
      )
    persist(context)
  }

  fun dictoByCategory(category: String): List<DictoEntry> =
    if (category == "all") state.dicto else state.dicto.filter { it.categories.contains(category) }

  fun allDictoTasks(): List<Pair<String, CapturedTask>> =
    state.dicto.flatMap { e -> e.tasks.map { e.id to it } }.sortedByDescending { it.second.createdAt }

  // ---- tasks (both sources) ------------------------------------------------------------------

  fun addQrTask(context: Context, task: CapturedTask) {
    load(context)
    state = state.copy(qrTasks = state.qrTasks + task)
    persist(context)
  }

  fun setQrTaskDone(context: Context, taskId: String, done: Boolean) {
    load(context)
    state = state.copy(qrTasks = state.qrTasks.map { if (it.id == taskId) it.copy(done = done) else it })
    persist(context)
  }

  /** Every task from both sources, newest first, tagged with where it came from. */
  fun taskRows(): List<TaskRow> =
    (state.qrTasks.map { TaskRow("qr", null, it) } +
        state.dicto.flatMap { e -> e.tasks.map { TaskRow("dicto", e.id, it) } })
      .sortedByDescending { it.task.createdAt }

  fun setTaskDoneByRow(context: Context, row: TaskRow, done: Boolean) {
    if (row.type == "qr") setQrTaskDone(context, row.task.id, done)
    else row.entryId?.let { setTaskDone(context, it, row.task.id, done) }
  }

  /** Reassigns a dictation task to a typed name (from the landing-page assignment flow). */
  fun setTaskOwner(context: Context, entryId: String, taskId: String, owner: String) {
    load(context)
    state =
      state.copy(
        dicto =
          state.dicto.map { e ->
            if (e.id == entryId) e.copy(tasks = e.tasks.map { t -> if (t.id == taskId) t.copy(owner = owner) else t })
            else e
          }
      )
    persist(context)
  }

  // ---- events ------------------------------------------------------------------------------

  fun addEvent(context: Context, event: CalendarEvent) {
    load(context)
    state = state.copy(events = (state.events + event).sortedBy { it.startAt })
    persist(context)
  }

  fun updateEvent(context: Context, event: CalendarEvent) {
    load(context)
    state = state.copy(events = state.events.map { if (it.id == event.id) event else it })
    persist(context)
  }

  fun deleteEvent(context: Context, id: String) {
    load(context)
    state = state.copy(events = state.events.filterNot { it.id == id })
    persist(context)
  }

  fun upcomingEvents(now: Long = System.currentTimeMillis()): List<CalendarEvent> =
    state.events.filter { it.startAt >= now && it.status != "done" }.sortedBy { it.startAt }

  fun pastEvents(now: Long = System.currentTimeMillis()): List<CalendarEvent> =
    state.events.filter { it.startAt < now || it.status == "done" }.sortedByDescending { it.startAt }

  // ---- meetings ----------------------------------------------------------------------------

  fun addMeeting(context: Context, meeting: MeetingArchive) {
    load(context)
    state = state.copy(meetings = listOf(meeting) + state.meetings)
    persist(context)
  }

  /** Creates the meeting on first submit, then updates the same record in place. */
  fun upsertMeeting(context: Context, meeting: MeetingArchive) {
    load(context)
    val exists = state.meetings.any { it.id == meeting.id }
    state =
      state.copy(
        meetings =
          if (exists) state.meetings.map { if (it.id == meeting.id) meeting else it }
          else listOf(meeting) + state.meetings
      )
    persist(context)
  }

  fun meetingById(id: String): MeetingArchive? = state.meetings.firstOrNull { it.id == id }
}
