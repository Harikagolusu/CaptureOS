package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.tool
import com.google.gson.annotations.SerializedName
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "CaptureOSPipeline"

/** What one capture produced. */
data class CaptureResult(val transcript: String, val summary: String, val tasks: List<CapturedTask>)

/** A dated thing the model found in a note. */
data class ExtractedEvent(
  val title: String,
  val kind: String, // meeting | task | deadline
  val startAt: Long,
  val priority: String,
  val owner: String,
)

/** What one Dicto capture produced: buckets, key points, a tidy rewrite, tasks and events. */
data class DictoExtraction(
  val categories: List<String>,
  val keyPoints: List<String>,
  val formatted: String,
  val tasks: List<CapturedTask>,
  val events: List<ExtractedEvent>,
)

private data class DictoJson(
  val categories: List<String> = emptyList(),
  @SerializedName("key_points") val keyPoints: List<String> = emptyList(),
  val formatted: String = "",
  val tasks: List<DictoTaskJson> = emptyList(),
  val events: List<DictoEventJson> = emptyList(),
)

private data class DictoTaskJson(
  val title: String = "",
  val priority: String = "medium",
  @SerializedName("due_date") val dueDate: String = "",
)

private data class DictoEventJson(
  val title: String = "",
  val kind: String = "task",
  @SerializedName("start_at") val startAt: String = "",
  val priority: String = "medium",
  val owner: String = "",
)

/**
 * Everything runs on the phone with the downloaded Gemma model — no network:
 * 1. each ≤30 s audio clip is transcribed by Gemma's audio input,
 * 2. the transcript (plus a photo, when one is attached) is read again to bucket the note,
 *    pull out tasks and find any dated events,
 * 3. tasks become reminders, events go to the shared calendar.
 */
class CapturePipeline(private val context: Context, private val model: Model) {

  private val instance: LlmModelInstance
    get() = model.instance as? LlmModelInstance ?: error("Model is not loaded yet")

  private fun dateContext(): Contents {
    val now = LocalDateTime.now()
    return Contents.of(
      listOf(
        Content.Text(
          "Today is ${now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd"))}, " +
            "time ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}. A weekday name means its next occurrence."
        )
      )
    )
  }

  /** Sends one message on a fresh conversation and returns the whole reply text. */
  private suspend fun ask(contents: List<Content>): String {
    val out = StringBuilder()
    instance.conversation.sendMessageAsync(Contents.of(contents)).collect { out.append(it.toString()) }
    return out.toString().trim()
  }

  suspend fun transcribe(clips: List<ByteArray>, onProgress: (done: Int, total: Int) -> Unit): String {
    val parts = mutableListOf<String>()
    clips.forEachIndexed { i, clip ->
      onProgress(i, clips.size)
      LlmChatModelHelper.resetConversation(
        model = model,
        supportImage = true,
        supportAudio = true,
        systemInstruction = null,
      )
      val text =
        ask(
          listOf(
            Content.AudioBytes(clip),
            Content.Text(
              "Transcribe this audio exactly as spoken (it may mix Telugu, Hindi and English). " +
                "Write English words in English letters. Output only the transcript."
            ),
          )
        )
      Log.d(TAG, "clip ${i + 1}/${clips.size}: $text")
      if (text.isNotBlank()) parts.add(text)
    }
    onProgress(clips.size, clips.size)
    return parts.joinToString(" ")
  }

  /**
   * Extracts a meeting transcript into the CaptureOS JSON schema (for QR handoff).
   * When [titleHint] is given the host's title is used verbatim; otherwise the model derives one.
   */
  suspend fun extractMeeting(transcript: String, teamCode: String, titleHint: String? = null): MeetingRecord {
    LlmChatModelHelper.resetConversation(
      model = model,
      supportImage = true,
      supportAudio = true,
      systemInstruction = null,
    )
    val titleLine =
      if (!titleHint.isNullOrBlank()) {
        "The host named this meeting: \"${titleHint.trim()}\". Use that exact string as meeting_title."
      } else {
        "Give the meeting a short, specific title (3-6 words) based on the transcript."
      }
    val raw =
      ask(
        listOf(
          Content.Text(
            captureOsJsonSchemaInstruction + "\n\n" +
              "team_code is \"$teamCode\".\n" +
              titleLine + "\n\n" +
              "Transcript:\n$transcript"
          )
        )
      )
    val meeting = parseMeetingJson(extractJsonObject(raw))
    // The host's title always wins, even if the model rewrites it.
    return if (!titleHint.isNullOrBlank()) meeting.copy(meetingTitle = titleHint.trim()) else meeting
  }

  /**
   * Reads a voice transcript together with an optional photo and decides where the note belongs,
   * what it contains, and whether it implies any dated events. Nothing is saved here.
   */
  suspend fun extractDicto(transcript: String, imagePath: String?): DictoExtraction {
    LlmChatModelHelper.resetConversation(
      model = model,
      supportImage = true,
      supportAudio = true,
      systemInstruction = null,
    )
    val now = LocalDateTime.now()
    val prompt =
      """
      You are CaptureOS's personal note assistant. Read the note below (a voice transcript, and a
      photo if one is attached) and return ONLY a JSON object:
      {
        "categories": ["to_do", "completed", "ideas", "mistakes"],
        "key_points": ["short factual takeaway"],
        "formatted": "a clean, well-formatted rewrite of the note",
        "tasks": [{"title": "imperative task", "priority": "high|medium|low", "due_date": "YYYY-MM-DD or empty"}],
        "events": [{"title": "...", "kind": "meeting|task|deadline", "start_at": "YYYY-MM-DD HH:mm", "priority": "high|medium|low", "owner": "..."}]
      }

      Categories are multi-label — include EVERY bucket that applies, in this vocabulary only:
      - "to_do": things the user still has to do.
      - "completed": things the user already did (past tense).
      - "ideas": brainstorming, thoughts, plans, things seen in the photo.
      - "mistakes": errors, regrets, reflections, things that went wrong.
      Use [] if none apply. Never invent a bucket name.

      Rules: 1-4 key points, sentence case, no markdown. Only include a task if the user actually
      wants to do something. Only include an event when a real date or time is stated or clearly
      implied; use the photo as context when it helps. Today is ${now.toLocalDate()}, now ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}.

      Note:
      $transcript
      """
        .trimIndent()
    val contents = mutableListOf<Content>()
    if (!imagePath.isNullOrBlank()) {
      runCatching { File(imagePath).readBytes() }.getOrNull()?.let { contents.add(Content.ImageBytes(it)) }
    }
    contents.add(Content.Text(prompt))
    val raw = ask(contents)
    val parsed =
      runCatching { meetingJsonGson.fromJson(extractJsonObject(raw), DictoJson::class.java) }.getOrNull() ?: DictoJson()
    val allowed = setOf("to_do", "completed", "ideas", "mistakes")
    val categories = parsed.categories.map { it.trim().lowercase() }.filter { it in allowed }.distinct()
    val tasks =
      parsed.tasks.filter { it.title.isNotBlank() }.map {
        CapturedTask(
          title = it.title.trim(),
          priority = it.priority.trim().lowercase().ifBlank { "medium" },
          dueDate = it.dueDate.trim(),
          source = "dicto",
        )
      }
    val events =
      parsed.events.filter { it.title.isNotBlank() }.mapNotNull { e ->
        val at = parseDateTime(e.startAt) ?: return@mapNotNull null
        ExtractedEvent(
          title = e.title.trim(),
          kind = e.kind.trim().lowercase().ifBlank { "task" },
          startAt = at,
          priority = e.priority.trim().lowercase().ifBlank { "medium" },
          owner = e.owner.trim(),
        )
      }
    return DictoExtraction(categories, parsed.keyPoints.filter { it.isNotBlank() }, parsed.formatted, tasks, events)
  }

  private fun parseDateTime(raw: String): Long? {
    val s = raw.trim()
    if (s.isBlank()) return null
    val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss")
    for (p in patterns) {
      runCatching {
          return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        .getOrNull()?.let { return it }
    }
    return runCatching {
        LocalDate.parse(s).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
      }
      .getOrNull()
  }

  private fun extractJsonObject(raw: String): String {
    var s = raw.trim().removePrefix("```json").removePrefix("```").trim()
    val start = s.indexOf('{')
    val end = s.lastIndexOf('}')
    if (start >= 0 && end > start) s = s.substring(start, end + 1)
    return s
  }

  /** Reads a transcript or typed note, lets the model call createTask, and returns the summary. */
  suspend fun extract(text: String, source: String): CaptureResult {
    val found = mutableListOf<CapturedTask>()
    val tools = listOf(tool(CaptureTools { found.add(it.copy(source = source)) }))
    LlmChatModelHelper.resetConversation(
      model = model,
      supportImage = true,
      supportAudio = true,
      systemInstruction = dateContext(),
      tools = tools,
    )
    val summary =
      ask(
        listOf(
          Content.Text(
            """
            Below is ${if (source == "meeting") "a meeting transcript" else "a note from the user"}.
            Call createTask once for every action item someone actually agreed to do (or the user asked to be reminded of).
            Skip small talk and ideas that were dropped. If someone refused, the task belongs to whoever took it.
            After the tool calls, reply with a 1-2 sentence English summary.

            $text
            """
              .trimIndent()
          )
        )
      )
    val saved =
      found.map { t ->
        val stored = TaskStore.add(context, t)
        val at = ReminderScheduler.schedule(context, stored)
        if (at > 0) stored.also { it.reminderAt = at; TaskStore.update(context, it) } else stored
      }
    return CaptureResult(transcript = text, summary = summary, tasks = saved)
  }
}
