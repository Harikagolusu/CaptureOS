package com.google.ai.edge.gallery.customtasks.captureos

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import java.nio.charset.StandardCharsets

object QrCodec {
  private const val QR_SIZE = 2048

  // Maximum payload a QR can hold (version 40, byte mode) at each EC level.
  private const val BYTES_L = 2953
  private const val BYTES_M = 2331

  /** Compact Gson: no HTML escaping (that inflates JSON with \u003c etc.), minified. */
  private val compactGson: Gson = GsonBuilder().disableHtmlEscaping().create()

  /**
   * Renders the meeting JSON as a QR, storing as much as the code can physically carry.
   * Uses EC level L (max capacity) first, then M. Only shrinks content when the raw JSON
   * exceeds the QR limit, trimming the least important fields first.
   */
  fun meetingJsonToQr(meeting: MeetingRecord): Bitmap {
    val payloads =
      listOf(
        ErrorCorrectionLevel.L to meeting,
        ErrorCorrectionLevel.M to meeting,
        ErrorCorrectionLevel.L to shrink(meeting, BYTES_L),
        ErrorCorrectionLevel.M to shrink(meeting, BYTES_M),
        ErrorCorrectionLevel.L to minimize(meeting),
      )
    for ((level, record) in payloads) {
      val json = compactGson.toJson(record)
      if (json.toByteArray(StandardCharsets.UTF_8).size <= byteBudget(level)) {
        runCatching { return render(json, level) }
      }
    }
    // Absolute last resort: a minimal payload that always fits.
    val minimal =
      compactGson.toJson(
        MeetingRecord(
          teamCode = meeting.teamCode.take(16),
          meetingTitle = meeting.meetingTitle.take(40),
          hostName = meeting.hostName.take(20),
          recordedAt = meeting.recordedAt,
          summary = "",
          decisions = emptyList(),
          actionItems =
            meeting.actionItems.take(4).map {
              ActionItem(
                task = it.task.take(30),
                owner = it.owner.take(15),
                deadline = it.deadline?.take(12),
                priority = it.priority.take(5),
                ownerConfidence = it.ownerConfidence.take(8),
              )
            },
        )
      )
    return render(minimal, ErrorCorrectionLevel.L)
  }

  private fun byteBudget(level: ErrorCorrectionLevel): Int = if (level == ErrorCorrectionLevel.L) BYTES_L else BYTES_M

  /**
   * Fits a meeting into [budget] bytes by dropping detail in priority order:
   * long summaries → many/short decisions → low-priority action items → owner/deadline text.
   * Never touches the meeting title or team code.
   */
  private fun shrink(meeting: MeetingRecord, budget: Int): MeetingRecord {
    val steps =
      listOf(
        meeting.copy(summary = meeting.summary, decisions = meeting.decisions, actionItems = meeting.actionItems),
        meeting.copy(summary = meeting.summary.take(400), decisions = meeting.decisions.take(6).map { it.take(120) }, actionItems = keepBest(meeting.actionItems, 12).map { it.shrinkTask(90) }),
        meeting.copy(summary = meeting.summary.take(240), decisions = meeting.decisions.take(4).map { it.take(100) }, actionItems = keepBest(meeting.actionItems, 8).map { it.shrinkTask(70) }),
        meeting.copy(summary = meeting.summary.take(120), decisions = meeting.decisions.take(3).map { it.take(80) }, actionItems = keepBest(meeting.actionItems, 6).map { it.shrinkTask(50) }),
      )
    for (candidate in steps) {
      val size = compactGson.toJson(candidate).toByteArray(StandardCharsets.UTF_8).size
      if (size <= budget) return candidate
    }
    // Even the smallest step is too big: aggressively flatten.
    return steps.last().copy(summary = "", decisions = emptyList(), actionItems = keepBest(meeting.actionItems, 4).map { it.shrinkTask(30) })
  }

  /** Keeps the most important action items (named owners, then high priority) within [max]. */
  private fun keepBest(items: List<ActionItem>, max: Int): List<ActionItem> {
    if (items.size <= max) return items
    val ranked = items.sortedWith(
      compareByDescending<ActionItem> { when (it.ownerConfidence) { "named" -> 2; "inferred" -> 1; else -> 0 } }
        .thenByDescending { when (it.priority) { "high" -> 2; "medium" -> 1; else -> 0 } },
    )
    return ranked.take(max)
  }

  private fun ActionItem.shrinkTask(n: Int): ActionItem =
    copy(task = task.take(n), owner = owner.take(24), deadline = deadline?.take(18), priority = priority.take(6), ownerConfidence = ownerConfidence.take(8))

  /** Tiny fallback that's guaranteed to encode. */
  private fun minimize(meeting: MeetingRecord): MeetingRecord =
    MeetingRecord(
      teamCode = meeting.teamCode.take(24),
      meetingTitle = meeting.meetingTitle.take(60),
      hostName = meeting.hostName.take(30),
      recordedAt = meeting.recordedAt,
      summary = meeting.summary.take(120),
      decisions = meeting.decisions.take(3).map { it.take(70) },
      actionItems = meeting.actionItems.take(6).map { it.shrinkTask(50) },
    )

  internal fun render(json: String, level: ErrorCorrectionLevel): Bitmap {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hints[EncodeHintType.ERROR_CORRECTION] = level
    hints[EncodeHintType.MARGIN] = 1
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    val matrix = QRCodeWriter().encode(json, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
    val bitmap = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
    for (x in 0 until QR_SIZE) {
      for (y in 0 until QR_SIZE) {
        bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
      }
    }
    return bitmap
  }

  /** Saves the QR as a PNG in the phone's Downloads collection. Returns its content Uri. */
  fun saveToDownloads(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    val values =
      ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(MediaStore.Downloads.IS_PENDING, 1)
        }
      }
    val resolver = context.contentResolver
    val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return null
    runCatching {
      resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
      }
    }
    return uri
  }

  fun shareIntent(uri: Uri): Intent =
    Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * Parses a scanned QR payload back into a meeting. Lenient: handles both compact and
 * escaped JSON, and defaults every missing field instead of failing.
 */
fun parseMeetingJson(json: String): MeetingRecord {
  val trimmed = json.trim()
  val raw =
    runCatching { QrCodecCompact.gson.fromJson(trimmed, MeetingRecord::class.java) }
      .getOrElse { throw IllegalArgumentException("Invalid meeting JSON") }
  return MeetingRecord(
    teamCode = raw.teamCode.orEmpty(),
    meetingTitle = raw.meetingTitle.orEmpty(),
    hostName = raw.hostName.orEmpty(),
    recordedAt = raw.recordedAt ?: 0L,
    summary = raw.summary.orEmpty(),
    decisions = raw.decisions.orEmpty(),
    actionItems =
      (raw.actionItems.orEmpty()).map {
        ActionItem(
          task = it.task.orEmpty(),
          owner = it.owner.orEmpty(),
          ownerConfidence = it.ownerConfidence.orEmpty(),
          deadline = it.deadline,
          priority = it.priority.orEmpty(),
        )
      },
  )
}

/** Package-private compact Gson shared with the encoder. */
internal object QrCodecCompact {
  val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
}