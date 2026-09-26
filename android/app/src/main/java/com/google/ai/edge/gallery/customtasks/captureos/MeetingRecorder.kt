package com.google.ai.edge.gallery.customtasks.captureos

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.abs

const val CAPTURE_SAMPLE_RATE = 16000
private const val BYTES_PER_SECOND = CAPTURE_SAMPLE_RATE * 2 // mono, 16-bit
const val CHUNK_SECONDS = 28 // Gemma's audio input takes up to 30 s per clip

/** Records 16 kHz mono PCM from the mic; the result is split into model-sized WAV clips. */
class MeetingRecorder {
  private var record: AudioRecord? = null
  private var worker: Thread? = null
  @Volatile private var running = false
  private val pcm = ByteArrayOutputStream()

  /** Latest input level 0..1 for the UI meter. */
  @Volatile var level: Float = 0f
    private set

  val seconds: Int
    get() = pcm.size() / BYTES_PER_SECOND

  @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO
  fun start() {
    val minBuf =
      AudioRecord.getMinBufferSize(CAPTURE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec =
      AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        CAPTURE_SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBuf * 4,
      )
    pcm.reset()
    record = rec
    running = true
    rec.startRecording()
    worker = thread(name = "captureos-recorder") {
      val buf = ByteArray(minBuf)
      while (running) {
        val n = rec.read(buf, 0, buf.size)
        if (n > 0) {
          synchronized(pcm) { pcm.write(buf, 0, n) }
          var peak = 0
          var i = 0
          while (i + 1 < n) {
            val s = (buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)
            peak = maxOf(peak, abs(s.toShort().toInt()))
            i += 2
          }
          level = peak / 32768f
        }
      }
    }
  }

  /** Stops and returns the recording as WAV clips of at most [CHUNK_SECONDS] each. */
  fun stop(): List<ByteArray> {
    running = false
    worker?.join(1000)
    record?.run {
      runCatching { stop() }
      release()
    }
    record = null
    val all = synchronized(pcm) { pcm.toByteArray() }
    return splitToWavClips(all)
  }

  fun cancel() {
    running = false
    worker?.join(1000)
    record?.run {
      runCatching { stop() }
      release()
    }
    record = null
    pcm.reset()
  }
}

/** Splits raw PCM into WAV clips; drops a trailing sliver under one second. */
fun splitToWavClips(pcm: ByteArray, chunkSeconds: Int = CHUNK_SECONDS): List<ByteArray> {
  val chunkBytes = chunkSeconds * BYTES_PER_SECOND
  val clips = mutableListOf<ByteArray>()
  var start = 0
  while (start < pcm.size) {
    val end = minOf(start + chunkBytes, pcm.size)
    if (end - start >= BYTES_PER_SECOND || clips.isEmpty()) clips.add(wav(pcm.copyOfRange(start, end)))
    start = end
  }
  return clips
}

private fun wav(pcm: ByteArray): ByteArray {
  val header =
    ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray())
      putInt(36 + pcm.size)
      put("WAVE".toByteArray())
      put("fmt ".toByteArray())
      putInt(16)
      putShort(1) // PCM
      putShort(1) // mono
      putInt(CAPTURE_SAMPLE_RATE)
      putInt(BYTES_PER_SECOND)
      putShort(2) // block align
      putShort(16) // bits per sample
      put("data".toByteArray())
      putInt(pcm.size)
    }
  return header.array() + pcm
}
