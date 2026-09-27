package com.google.ai.edge.gallery.customtasks.captureos

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodec {
  private const val QR_SIZE = 1024

  /**
   * Renders the meeting JSON as a QR. Tries EC level M first, falls back to L (more capacity), and
   * finally trims the summary/decisions so a long meeting still produces a scannable code instead
   * of throwing.
   */
  fun meetingJsonToQr(meeting: MeetingRecord): Bitmap {
    val attempts =
      listOf(
        meeting to ErrorCorrectionLevel.M,
        meeting to ErrorCorrectionLevel.L,
        meeting.trimmed() to ErrorCorrectionLevel.L,
        meeting.trimmed().trimmed() to ErrorCorrectionLevel.L,
      )
    for ((record, level) in attempts) {
      runCatching { return render(meetingJsonGson.toJson(record), level) }
    }
    // Last resort: a minimal payload that always fits.
    return render(
      meetingJsonGson.toJson(
        MeetingRecord(
          teamCode = meeting.teamCode,
          meetingTitle = meeting.meetingTitle.take(60),
          summary = "",
          decisions = emptyList(),
          actionItems = meeting.actionItems.take(8),
        )
      ),
      ErrorCorrectionLevel.L,
    )
  }

  private fun render(json: String, level: ErrorCorrectionLevel): Bitmap {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hints[EncodeHintType.ERROR_CORRECTION] = level
    hints[EncodeHintType.MARGIN] = 1
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

private fun MeetingRecord.trimmed(): MeetingRecord =
  copy(
    summary = summary.take(140),
    decisions = decisions.take(4).map { it.take(90) },
    actionItems = actionItems.take(10).map { it.copy(task = it.task.take(80)) },
  )

fun parseMeetingJson(json: String): MeetingRecord = meetingJsonGson.fromJson(json, MeetingRecord::class.java)
