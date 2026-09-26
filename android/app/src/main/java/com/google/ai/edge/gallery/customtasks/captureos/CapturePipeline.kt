package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.tool
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "CaptureOSPipeline"

/** What one capture produced. */
data class CaptureResult(val transcript: String, val summary: String, val tasks: List<CapturedTask>)

/**
 * Everything runs on the phone with the downloaded Gemma model — no network:
 * 1. each ≤30 s audio clip is transcribed by Gemma's audio input,
 * 2. the full transcript is read once more with the createTask tool available,
 * 3. every task is saved and gets a reminder notification.
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
      LlmChatModelHelper.resetConversation(model = model, supportAudio = true, systemInstruction = null)
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

  /** Reads a transcript or typed note, lets the model call createTask, and returns the summary. */
  suspend fun extract(text: String, source: String): CaptureResult {
    val found = mutableListOf<CapturedTask>()
    val tools = listOf(tool(CaptureTools { found.add(it.copy(source = source)) }))
    LlmChatModelHelper.resetConversation(model = model, supportAudio = true, systemInstruction = dateContext(), tools = tools)
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
