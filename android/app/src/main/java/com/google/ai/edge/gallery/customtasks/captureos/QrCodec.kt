package com.google.ai.edge.gallery.customtasks.captureos

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodec {
  private const val QR_SIZE = 1024

  fun meetingJsonToQr(meeting: MeetingRecord): Bitmap {
    val json = meetingJsonGson.toJson(meeting)
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
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
}

fun parseMeetingJson(json: String): MeetingRecord = meetingJsonGson.fromJson(json, MeetingRecord::class.java)