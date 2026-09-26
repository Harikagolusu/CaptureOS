package com.google.ai.edge.gallery.customtasks.captureos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
  data class Transcribing(val done: Int, val total: Int) : AdminStage
  data object Thinking : AdminStage
  data class Ready(val meeting: MeetingRecord, val transcript: String) : AdminStage
  data class Failed(val message: String) : AdminStage
}

@Composable
fun AdminScreen(profile: SetupProfile, model: com.google.ai.edge.gallery.data.Model?, bottomPadding: androidx.compose.ui.unit.Dp) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val recorder = remember { MeetingRecorder() }
  var stage by remember { mutableStateOf<AdminStage>(AdminStage.Idle) }
  var seconds by remember { mutableIntStateOf(0) }
  var level by remember { mutableFloatStateOf(0f) }

  val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
  LaunchedEffect(Unit) {
    permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
  }
  LaunchedEffect(stage) {
    while (stage == AdminStage.Recording) {
      seconds = recorder.seconds
      level = recorder.level
      delay(200)
    }
  }

  fun run(block: suspend (CapturePipeline) -> AdminStage) {
    if (model == null) return
    scope.launch {
      try {
        val r = withContext(Dispatchers.Default) { block(CapturePipeline(context, model)) }
        stage = r
      } catch (e: Exception) {
        stage = AdminStage.Failed(e.message ?: "Something went wrong")
      }
    }
  }

  fun toggleRecording() {
    if (stage == AdminStage.Recording) {
      val clips = recorder.stop()
      stage = AdminStage.Transcribing(0, clips.size)
      run { p ->
        val transcript = p.transcribe(clips) { d, t -> stage = AdminStage.Transcribing(d, t) }
        stage = AdminStage.Thinking
        AdminStage.Ready(p.extractMeeting(transcript, profile.teamCode), transcript)
      }
    } else {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        return
      }
      recorder.start()
      stage = AdminStage.Recording
    }
  }

  fun runDemo() {
    stage = AdminStage.Thinking
    run { p -> AdminStage.Ready(p.extractMeeting(DEMO_TRANSCRIPT, profile.teamCode), DEMO_TRANSCRIPT) }
  }

  if (model == null) {
    Column(
      Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      CircularProgressIndicator()
      Spacer(Modifier.height(16.dp))
      Text("Loading Gemma on your phone…", textAlign = TextAlign.Center)
    }
    return
  }

  when (val s = stage) {
    is AdminStage.Ready -> QrView(meeting = s.meeting, transcript = s.transcript, onBack = { stage = AdminStage.Idle })
    else -> RecordView(
      stage = s,
      seconds = seconds,
      level = level,
      onRecord = ::toggleRecording,
      onDemo = ::runDemo,
      onDismissError = { stage = AdminStage.Idle },
      bottomPadding = bottomPadding,
    )
  }
}

@Composable
private fun RecordView(
  stage: AdminStage,
  seconds: Int,
  level: Float,
  onRecord: () -> Unit,
  onDemo: () -> Unit,
  onDismissError: () -> Unit,
  bottomPadding: androidx.compose.ui.unit.Dp,
) {
  val busy = stage is AdminStage.Transcribing || stage == AdminStage.Thinking
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(24.dp))
    Text("Admin · Record the meeting", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(24.dp))
    val recording = stage == AdminStage.Recording
    FilledIconButton(
      onClick = onRecord,
      enabled = !busy,
      modifier = Modifier.size(140.dp),
      shape = CircleShape,
      colors =
        IconButtonDefaults.filledIconButtonColors(
          containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ),
    ) {
      Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(64.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(
      when (stage) {
        AdminStage.Recording -> "Recording  %02d:%02d".format(seconds / 60, seconds % 60)
        is AdminStage.Transcribing -> "Transcribing on-device… part ${stage.done + 1} of ${stage.total}"
        AdminStage.Thinking -> "Extracting decisions and action items…"
        else -> "Tap to record"
      },
      style = MaterialTheme.typography.titleMedium,
    )
    if (recording) {
      Spacer(Modifier.height(8.dp))
      LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, modifier = Modifier.width(200.dp))
    }
    if (busy) {
      Spacer(Modifier.height(8.dp))
      LinearProgressIndicator(modifier = Modifier.width(200.dp))
    }
    Text(
      "Offline · Gemma runs on this phone · nothing is uploaded",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (stage is AdminStage.Failed) {
      Spacer(Modifier.height(8.dp))
      Text(stage.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
      TextButton(onClick = onDismissError) { Text("OK") }
    }
    Spacer(Modifier.height(32.dp))
    OutlinedButton(onClick = onDemo, enabled = !busy) { Text("Use demo script (reliable)") }
  }
}

@Composable
private fun QrView(meeting: MeetingRecord, transcript: String, onBack: () -> Unit) {
  val qr: Bitmap = remember(meeting) { QrCodec.meetingJsonToQr(meeting) }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(meeting.meetingTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text("Team ${meeting.teamCode} · scan to receive your tasks", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    Image(
      bitmap = qr.asImageBitmap(),
      contentDescription = "Meeting QR",
      modifier = Modifier.fillMaxWidth().aspectRatio(1f),
    )
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp)) {
        Text("Summary", fontWeight = FontWeight.Bold)
        Text(meeting.summary)
        if (meeting.decisions.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text("Decisions", fontWeight = FontWeight.Bold)
          meeting.decisions.forEach { Text("• $it") }
        }
        Spacer(Modifier.height(8.dp))
        Text("${meeting.actionItems.size} action item(s)", fontWeight = FontWeight.Bold)
        meeting.actionItems.forEach { a ->
          Text(
            "• ${a.task} — ${a.owner} (${a.ownerConfidence})",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onBack) { Text("Record another") }
  }
}