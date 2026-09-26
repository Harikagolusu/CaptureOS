package com.google.ai.edge.gallery.customtasks.captureos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Stage {
  data object Idle : Stage
  data object Recording : Stage
  data class Transcribing(val done: Int, val total: Int) : Stage
  data object Thinking : Stage
  data class Failed(val message: String) : Stage
}

@Composable
fun CaptureScreen(modelManagerViewModel: ModelManagerViewModel, bottomPadding: Dp) {
  val context = LocalContext.current
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val model = uiState.selectedModel
  val scope = rememberCoroutineScope()
  val recorder = remember { MeetingRecorder() }
  var stage by remember { mutableStateOf<Stage>(Stage.Idle) }
  var result by remember { mutableStateOf<CaptureResult?>(null) }
  var note by remember { mutableStateOf("") }
  var tab by remember { mutableIntStateOf(0) }
  var seconds by remember { mutableIntStateOf(0) }
  var level by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    TaskStore.load(context)
    ReminderScheduler.ensureChannel(context)
  }
  val permissions =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
  LaunchedEffect(Unit) {
    permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
  }
  LaunchedEffect(stage) {
    while (stage == Stage.Recording) {
      seconds = recorder.seconds
      level = recorder.level
      delay(200)
    }
  }

  fun run(block: suspend (CapturePipeline) -> CaptureResult) {
    scope.launch {
      try {
        val r = withContext(Dispatchers.Default) { block(CapturePipeline(context, model)) }
        result = r
        stage = Stage.Idle
      } catch (e: Exception) {
        stage = Stage.Failed(e.message ?: "Something went wrong")
      }
    }
  }

  fun toggleRecording() {
    if (stage == Stage.Recording) {
      val clips = recorder.stop()
      stage = Stage.Transcribing(0, clips.size)
      run { p ->
        val transcript = p.transcribe(clips) { d, t -> stage = Stage.Transcribing(d, t) }
        stage = Stage.Thinking
        p.extract(transcript, source = "meeting")
      }
    } else {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        return
      }
      result = null
      recorder.start()
      stage = Stage.Recording
    }
  }

  fun sendNote() {
    val text = note.trim()
    if (text.isEmpty()) return
    note = ""
    stage = Stage.Thinking
    run { p -> p.extract(text, source = "note") }
  }

  if (!uiState.isModelInitialized(model = model)) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      CircularProgressIndicator()
      Spacer(Modifier.height(16.dp))
      Text("Loading ${model.name} on your phone…", textAlign = TextAlign.Center)
      Text("Runs fully offline — nothing leaves the device.", style = MaterialTheme.typography.bodySmall)
    }
    return
  }

  Column(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
    TabRow(selectedTabIndex = tab) {
      Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Capture") })
      Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Tasks (${TaskStore.tasks.count { !it.done }})") })
    }
    if (tab == 0) {
      CaptureTab(
        stage = stage,
        seconds = seconds,
        level = level,
        result = result,
        note = note,
        onNote = { note = it },
        onSend = ::sendNote,
        onRecord = ::toggleRecording,
        onDismissError = { stage = Stage.Idle },
      )
    } else {
      TasksTab()
    }
  }
}

@Composable
private fun CaptureTab(
  stage: Stage,
  seconds: Int,
  level: Float,
  result: CaptureResult?,
  note: String,
  onNote: (String) -> Unit,
  onSend: () -> Unit,
  onRecord: () -> Unit,
  onDismissError: () -> Unit,
) {
  val busy = stage is Stage.Transcribing || stage == Stage.Thinking
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(12.dp))
    val recording = stage == Stage.Recording
    FilledIconButton(
      onClick = onRecord,
      enabled = !busy,
      modifier = Modifier.size(120.dp),
      shape = CircleShape,
      colors =
        IconButtonDefaults.filledIconButtonColors(
          containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ),
    ) {
      Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(56.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(
      when (stage) {
        Stage.Recording -> "Recording  %02d:%02d".format(seconds / 60, seconds % 60)
        is Stage.Transcribing -> "Listening on-device… part ${stage.done + 1} of ${stage.total}"
        Stage.Thinking -> "Finding tasks and owners…"
        else -> "Tap to record a meeting"
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
    if (stage is Stage.Failed) {
      Spacer(Modifier.height(8.dp))
      Text(stage.message, color = MaterialTheme.colorScheme.error)
      TextButton(onClick = onDismissError) { Text("OK") }
    }

    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = note,
        onValueChange = onNote,
        enabled = !busy && !recording,
        modifier = Modifier.weight(1f),
        placeholder = { Text("Or type: remind Kiran to send the invoice Friday 5 pm") },
      )
      IconButton(onClick = onSend, enabled = !busy && !recording && note.isNotBlank()) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
      }
    }

    if (result != null) {
      Spacer(Modifier.height(20.dp))
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
          Text("Summary", fontWeight = FontWeight.Bold)
          Text(result.summary.ifBlank { "—" })
          Spacer(Modifier.height(12.dp))
          Text(
            if (result.tasks.isEmpty()) "No tasks found" else "${result.tasks.size} task(s) saved",
            fontWeight = FontWeight.Bold,
          )
          result.tasks.forEach { TaskLine(it) }
          if (result.transcript.isNotBlank() && result.tasks.firstOrNull()?.source != "note") {
            Spacer(Modifier.height(12.dp))
            Text("Transcript", fontWeight = FontWeight.Bold)
            Text(result.transcript, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

@Composable
private fun TaskLine(t: CapturedTask) {
  Column(Modifier.padding(vertical = 4.dp)) {
    Text("☐ ${t.title}")
    val meta = listOfNotNull(
      t.owner.ifBlank { null },
      t.dueDate.ifBlank { null }?.let { d -> if (t.dueTime.isNotBlank()) "$d ${t.dueTime}" else d },
      t.priority,
    ).joinToString(" · ")
    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (t.reminderAt > 0) ReminderChip(t.reminderAt)
  }
}

@Composable
private fun ReminderChip(at: Long) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 2.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
  ) {
    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
    Text(
      "Reminder " + SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(at)),
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

@Composable
private fun TasksTab() {
  val context = LocalContext.current
  val tasks = TaskStore.tasks
  if (tasks.isEmpty()) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
      Text("No tasks yet. Record a meeting or type a note.", textAlign = TextAlign.Center)
    }
    return
  }
  LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    items(tasks.sortedWith(compareBy<CapturedTask> { it.done }.thenBy { it.dueDate.ifBlank { "9999" } }), key = { it.id }) { t ->
      Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = t.done, onCheckedChange = { TaskStore.setDone(context, t.id, it) })
          Column(Modifier.weight(1f)) {
            Text(t.title, textDecoration = if (t.done) TextDecoration.LineThrough else null)
            Text(
              listOfNotNull(t.owner.ifBlank { null }, t.dueDate.ifBlank { null }, t.priority).joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
            )
            if (t.reminderAt > System.currentTimeMillis() && !t.done) ReminderChip(t.reminderAt)
          }
          IconButton(onClick = { TaskStore.delete(context, t.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        }
      }
    }
  }
}
