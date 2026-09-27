package com.google.ai.edge.gallery.customtasks.captureos

import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.StandardCharsets

/** A single assignable task, carried in a QR so an employee can scan and accept it. */
data class TaskCode(
  val title: String = "",
  val owner: String = "",
  val priority: String = "medium",
  val due: String = "",
  val note: String = "",
)

object TaskQrCodec {
  private const val MAX_BYTES = 2331

  private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

  /**
   * Renders a task as a scannable QR. Always use the compact (non-HTML-escaped) writer so the
   * payload stays tiny; the scanner uses the same compact reader.
   */
  fun taskToQr(task: TaskCode): Bitmap {
    val json = gson.toJson(task)
    // If somehow too big, trim and retry before falling back.
    val safe =
      if (json.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) json
      else
        gson.toJson(
          task.copy(
            title = task.title.take(140),
            owner = task.owner.take(30),
            note = task.note.take(120),
          )
        )
    return QrCodec.render(safe, ErrorCorrectionLevel.M)
  }

  /** Parses a scanned task QR payload, tolerating missing/defaulted fields. */
  fun parseTaskJson(json: String): TaskCode {
    val raw = gson.fromJson(json.trim(), TaskCode::class.java)
      ?: throw IllegalArgumentException("Invalid task JSON")
    return TaskCode(
      title = raw.title.orEmpty(),
      owner = raw.owner.orEmpty(),
      priority = raw.priority.orEmpty().ifBlank { "medium" },
      due = raw.due.orEmpty(),
      note = raw.note.orEmpty(),
    )
  }

  fun toJson(task: TaskCode): String = gson.toJson(task)
}