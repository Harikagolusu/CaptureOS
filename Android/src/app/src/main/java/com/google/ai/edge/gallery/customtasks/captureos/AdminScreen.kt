package com.google.ai.edge.gallery.customtasks.captureos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import java.util.UUID
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEMO_TRANSCRIPT =
  "Priya, can you get the client deck updated by Thursday? We decided to push the launch to next month. " +
    "Someone needs to follow up with the vendor about the contract — I'll take care of that one. " +
    "And we still haven't figured out who's handling the office move."

private sealed interface AdminStage {
  data object Idle : AdminStage
  data object Recording : AdminStage
  data class Processing(val step: Int, val meta: String) : AdminStage
  data class Qr(val meeting: MeetingRecord, val transcript: String) : AdminStage
  data class Failed(val message: String, val transcript: String) : AdminStage
}

@Composable
fun AdminScreen(profile: SetupProfile, model: Model?, bottomPadding: Dp, onScan: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val recorder = remember { MeetingRecorder() }
  var stage by remember { mutableStateOf<AdminStage>(AdminStage.Idle) }
  var seconds by remember { mutableIntStateOf(0) }
  var meetingTitle by remember { mutableStateOf("") }
  var meetingId by remember { mutableStateOf<String?>(null) }

  val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
  LaunchedEffect(Unit) {
    permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
  }
  LaunchedEffect(stage) {
    while (stage == AdminStage.Recording) {
      seconds = recorder.seconds
      delay(200)
    }
  }

  fun run(block: suspend (CapturePipeline) -> AdminStage) {
    if (model == null) return
    scope.launch {
      try {
        stage = withContext(Dispatchers.Default) { block(CapturePipeline(context, model)) }
      } catch (e: Exception) {
        stage = AdminStage.Failed(e.message ?: "Something went wrong", "")
      }
    }
  }

  fun ensureMeeting(): String {
    val title = meetingTitle.trim().ifBlank { "(untitled meeting)" }
    val id = meetingId ?: UUID.randomUUID().toString().also { meetingId = it }
    val existing = CaptureDb.meetingById(id)
    val record =
      (existing ?: MeetingArchive(id = id, teamCode = profile.teamCode, meetingTitle = title))
        .copy(meetingTitle = title, hostName = profile.name, teamCode = profile.teamCode)
    CaptureDb.upsertMeeting(context, record)
    if (existing == null) CaptureDb.log(context, "meeting", "Created \"$title\"")
    return id
  }

  fun archiveMeeting(meeting: MeetingRecord, transcript: String) {
    val id = ensureMeeting()
    val existing = CaptureDb.meetingById(id)
    val record =
      (existing ?: MeetingArchive(id = id, teamCode = meeting.teamCode, meetingTitle = meeting.meetingTitle))
        .copy(
          teamCode = meeting.teamCode,
          meetingTitle = meeting.meetingTitle.ifBlank { meetingTitle.trim().ifBlank { "(untitled meeting)" } },
          transcript = transcript,
          summary = meeting.summary,
          decisions = meeting.decisions,
          actionItems = meeting.actionItems,
          status = "recorded",
        )
    CaptureDb.upsertMeeting(context, record)
    CaptureDb.log(context, "meeting", "Recorded ${record.meetingTitle} · ${meeting.actionItems.size} tasks")
    meeting.actionItems.forEach { a ->
      val at = parseDeadline(a.deadline) ?: return@forEach
      val event =
        CalendarEvent(
          title = a.task,
          kind = "task",
          startAt = at,
          priority = a.priority,
          owner = a.owner,
          sourceId = record.id,
        )
      val calendarId = CalendarWriter.write(context, event)
      CaptureDb.addEvent(context, event.copy(calendarEventId = calendarId))
      ReminderChain.schedule(context, event)
    }
  }

  fun process(clips: List<ByteArray>) {
    stage = AdminStage.Processing(0, "")
    run { p ->
      val transcript = p.transcribe(clips) { d, t -> stage = AdminStage.Processing(0, "part ${d + 1} of $t") }
      stage = AdminStage.Processing(1, "")
      val meeting = p.extractMeeting(transcript, profile.teamCode, meetingTitle)
      archiveMeeting(meeting, transcript)
      AdminStage.Qr(meeting, transcript)
    }
  }

  fun toggleRecording() {
    if (stage == AdminStage.Recording) {
      process(recorder.stop())
    } else {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        return
      }
      ensureMeeting()
      recorder.start()
      stage = AdminStage.Recording
    }
  }

  fun runDemo() {
    ensureMeeting()
    stage = AdminStage.Processing(1, "")
    run { p ->
      val meeting = p.extractMeeting(DEMO_TRANSCRIPT, profile.teamCode, meetingTitle)
      archiveMeeting(meeting, DEMO_TRANSCRIPT)
      AdminStage.Qr(meeting, DEMO_TRANSCRIPT)
    }
  }

  when (val s = stage) {
    is AdminStage.Qr ->
      QrDisplay(
        meeting = s.meeting,
        onBack = {
          stage = AdminStage.Idle
          meetingId = null
        },
      )
    is AdminStage.Processing -> ProcessingScreen(step = s.step, meta = s.meta)
    is AdminStage.Failed ->
      ErrorState(
        message = s.message,
        onRetry = { stage = AdminStage.Idle },
        bottomPadding = bottomPadding,
      )
    else ->
      MeetingRecordScreen(
        recording = s == AdminStage.Recording,
        seconds = seconds,
        title = meetingTitle,
        onTitleChange = { newTitle ->
          meetingTitle = newTitle
          // Naming the meeting creates it right away, and renames it on every edit.
          if (newTitle.isNotBlank()) ensureMeeting()
        },
        onRecord = ::toggleRecording,
        onDemo = ::runDemo,
        onScan = onScan,
        bottomPadding = bottomPadding,
      )
  }
}

@Composable
private fun MeetingRecordScreen(
  recording: Boolean,
  seconds: Int,
  title: String,
  onTitleChange: (String) -> Unit,
  onRecord: () -> Unit,
  onDemo: () -> Unit,
  onScan: () -> Unit,
  bottomPadding: Dp,
) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(horizontal = 20.dp),
  ) {
    Spacer(Modifier.height(24.dp))
    ScreenTitle(title.trim().ifBlank { "New Meeting" })
    Spacer(Modifier.height(6.dp))
    Text(
      "Name it so everyone knows which meeting this is.",
      style = MaterialTheme.typography.bodySmall,
      color = CaptureColors.Neutral,
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
      value = title,
      onValueChange = onTitleChange,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      enabled = !recording,
      placeholder = { Text("Meeting title (optional)", color = CaptureColors.Neutral) },
      shape = RoundedCornerShape(8.dp),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedBorderColor = CaptureColors.Navy,
          unfocusedBorderColor = CaptureColors.Hairline,
          cursorColor = CaptureColors.Navy,
        ),
    )
    Spacer(Modifier.height(32.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RecordButton(recording = recording, onClick = onRecord)
        Spacer(Modifier.height(18.dp))
        RecordTimer(seconds)
      }
    }
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Text(
        if (recording) "Tap to stop" else "Tap to record",
        style = MaterialTheme.typography.bodyMedium,
        color = CaptureColors.Neutral,
      )
    }
    Spacer(Modifier.height(28.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      TextButton(onClick = onDemo, enabled = !recording) {
        Text("Use demo script", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
      }
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      TextButton(onClick = onScan, enabled = !recording) {
        Text("Scan a meeting QR", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
      }
    }
  }
}

@Composable
private fun ProcessingScreen(step: Int, meta: String) {
  Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    Spacer(Modifier.height(32.dp))
    ScreenTitle("Processing")
    Spacer(Modifier.height(28.dp))
    LoadingSteps(steps = listOf("Transcribing…", "Extracting decisions and tasks…"), currentIndex = step)
    if (meta.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      Meta(meta)
    }
  }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, bottomPadding: Dp) {
  Column(Modifier.fillMaxSize().padding(bottom = bottomPadding).padding(horizontal = 20.dp)) {
    Spacer(Modifier.height(32.dp))
    ScreenTitle("Couldn't process that recording")
    Spacer(Modifier.height(10.dp))
    Text(message, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
    Spacer(Modifier.height(28.dp))
    PrimaryButton("Try again", onClick = onRetry)
  }
}

@Composable
private fun QrDisplay(meeting: MeetingRecord, onBack: () -> Unit) {
  val context = LocalContext.current
  val qr: Bitmap = remember(meeting) { QrCodec.meetingJsonToQr(meeting) }
  Column(
    Modifier.fillMaxSize().background(CaptureColors.Navy).padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(8.dp))
    Text(meeting.meetingTitle, style = MaterialTheme.typography.titleLarge, color = CaptureColors.Paper)
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(CaptureColors.Paper)) {
      Image(bitmap = qr.asImageBitmap(), contentDescription = "Meeting QR", modifier = Modifier.fillMaxSize().padding(16.dp))
    }
    Spacer(Modifier.height(20.dp))
    Text("Show this to your team", style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Paper)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TextButton(onClick = {
        val uri = QrCodec.saveToDownloads(context, qr, "captureos_meeting_${System.currentTimeMillis()}.png")
        if (uri != null) context.startActivity(Intent.createChooser(QrCodec.shareIntent(uri), "Share QR"))
      }) {
        Text("Save & share QR", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Paper)
      }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onBack) {
      Text("Record another", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Paper.copy(alpha = 0.7f))
    }
  }
}

private fun parseDeadline(raw: String?): Long? {
  val s = raw?.trim().orEmpty()
  if (s.isBlank()) return null
  val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm")
  for (p in patterns) {
    runCatching {
        return java.time.LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern(p))
          .atZone(java.time.ZoneId.systemDefault())
          .toInstant()
          .toEpochMilli()
      }
      .getOrNull()?.let { return it }
  }
  return runCatching {
      java.time.LocalDate.parse(s).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    .getOrNull()
}
