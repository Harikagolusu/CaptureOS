package com.google.ai.edge.gallery.customtasks.captureos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.Model
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val dictoDateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

val DICTO_BUCKETS =
  listOf(
    "all" to "All",
    "to_do" to "To-Do List",
    "completed" to "Completed",
    "ideas" to "Ideas",
    "mistakes" to "Mistakes",
  )

fun categoryLabel(category: String): String =
  when (category) {
    "to_do" -> "To-Do"
    "completed" -> "Completed"
    "ideas" -> "Ideas"
    "mistakes" -> "Mistakes"
    else -> "Uncategorised"
  }

fun categoryColor(category: String): Color =
  when (category) {
    "to_do" -> CaptureColors.Medium
    "completed" -> CaptureColors.Low
    "ideas" -> CaptureColors.Navy
    "mistakes" -> CaptureColors.High
    else -> CaptureColors.Neutral
  }

private sealed interface DictoStage {
  data object Idle : DictoStage
  data class Processing(val step: Int, val meta: String) : DictoStage
  data class Saved(val buckets: Int, val tasks: Int, val events: Int) : DictoStage
  data class Failed(val message: String) : DictoStage
}

/** Dicto capture: optional photo + voice note → transcript → buckets, tasks and events. */
@Composable
fun DictoCaptureScreen(model: Model?, bottomPadding: Dp) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val recorder = remember { MeetingRecorder() }
  var stage by remember { mutableStateOf<DictoStage>(DictoStage.Idle) }
  var seconds by remember { mutableIntStateOf(0) }
  var recording by remember { mutableStateOf(false) }
  var imagePath by remember { mutableStateOf<String?>(null) }

  val permissions =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        val dest = File(CaptureDb.imageDir(context), "${UUID.randomUUID()}.jpg")
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
        imagePath = dest.absolutePath
      }
    }

  LaunchedEffect(Unit) {
    permissions.launch(
      arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
      )
    )
  }
  LaunchedEffect(recording) {
    while (recording) {
      seconds = recorder.seconds
      delay(200)
    }
  }
  LaunchedEffect(stage) {
    if (stage is DictoStage.Saved) {
      delay(2200)
      stage = DictoStage.Idle
      imagePath = null
    }
  }

  fun process(clips: List<ByteArray>) {
    if (model == null) return
    stage = DictoStage.Processing(0, "")
    scope.launch {
      try {
        var transcript = ""
        val result =
          withContext(Dispatchers.Default) {
            val p = CapturePipeline(context, model)
            transcript = p.transcribe(clips) { d, t -> stage = DictoStage.Processing(0, "part ${d + 1} of $t") }
            stage = DictoStage.Processing(1, "")
            p.extractDicto(transcript, imagePath)
          }
        val id = UUID.randomUUID().toString()
        val audioFile = File(CaptureDb.audioDir(context), "$id.wav")
        runCatching { audioFile.writeBytes(toWavBytes(recorder.lastPcm)) }
        CaptureDb.addDicto(
          context,
          DictoEntry(
            id = id,
            transcript = transcript,
            audioPath = audioFile.absolutePath.takeIf { audioFile.exists() },
            imagePath = imagePath,
            categories = result.categories,
            keyPoints = result.keyPoints,
            formatted = result.formatted,
            tasks = result.tasks,
          )
        )
        result.tasks.forEach { ReminderScheduler.schedule(context, it) }
        result.events.forEach { e ->
          val event =
            CalendarEvent(
              title = e.title,
              kind = e.kind,
              startAt = e.startAt,
              priority = e.priority,
              owner = e.owner,
              sourceId = id,
            )
          val calendarId = CalendarWriter.write(context, event)
          CaptureDb.addEvent(context, event.copy(calendarEventId = calendarId))
          ReminderChain.schedule(context, event)
        }
        CaptureDb.log(context, "dicto", "Saved a note · ${result.categories.size} buckets · ${result.events.size} events")
        stage = DictoStage.Saved(result.categories.size, result.tasks.size, result.events.size)
      } catch (e: Exception) {
        stage = DictoStage.Failed(e.message ?: "Something went wrong")
      }
    }
  }

  fun toggleRecording() {
    if (!recording) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        permissions.launch(
          arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
          )
        )
        return
      }
      recorder.start()
      recording = true
    } else {
      recording = false
      process(recorder.stop())
    }
  }

  when (val s = stage) {
    is DictoStage.Processing ->
      Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(32.dp))
        ScreenTitle("Processing")
        Spacer(Modifier.height(28.dp))
        LoadingSteps(listOf("Transcribing…", "Sorting into buckets and finding dates…"), currentIndex = s.step)
        if (s.meta.isNotBlank()) {
          Spacer(Modifier.height(10.dp))
          Meta(s.meta)
        }
      }
    is DictoStage.Saved ->
      Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Saved ✓")
        Spacer(Modifier.height(10.dp))
        Text(
          buildString {
            append(if (s.buckets == 1) "1 bucket" else "${s.buckets} buckets")
            append(" · ")
            append(if (s.tasks == 1) "1 task" else "${s.tasks} tasks")
            if (s.events > 0) {
              append(" · ")
              append(if (s.events == 1) "1 event scheduled" else "${s.events} events scheduled")
            }
          },
          style = MaterialTheme.typography.bodyLarge,
          color = CaptureColors.Ink,
        )
      }
    is DictoStage.Failed ->
      Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(32.dp))
        ScreenTitle("Couldn't process that recording")
        Spacer(Modifier.height(10.dp))
        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Try again", onClick = { stage = DictoStage.Idle })
      }
    else ->
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(horizontal = 20.dp),
      ) {
        Spacer(Modifier.height(24.dp))
        ScreenTitle("Dicto")
        Spacer(Modifier.height(4.dp))
        Text("Say it, or show it — Gemma sorts it for you.", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RecordButton(recording = recording, onClick = ::toggleRecording)
            Spacer(Modifier.height(18.dp))
            RecordTimer(seconds)
          }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          Text(
            if (recording) "Tap to stop" else "Tap to record",
            style = MaterialTheme.typography.bodyMedium,
            color = CaptureColors.Neutral,
          )
        }
        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text("Photo (optional)", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral, modifier = Modifier.weight(1f))
          TextButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !recording,
          ) {
            Text(if (imagePath == null) "Add photo" else "Change photo", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
          }
        }
        imagePath?.let { path ->
          Spacer(Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            val bmp = remember(path) { BitmapFactory.decodeFile(path) }
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
              )
            }
            Spacer(Modifier.width(12.dp))
            Text("Gemma will read this photo too.", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral, modifier = Modifier.weight(1f))
            TextButton(onClick = { imagePath = null }, enabled = !recording) {
              Text("Remove", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
            }
          }
        }
      }
  }
}

private val TASK_TYPES = listOf("all" to "All", "qr" to "QR", "dicto" to "Dictation")

/** Tasks page: every task from QR check-ins and dictation, filterable by where it came from. */
@Composable
fun TasksScreen(bottomPadding: Dp) {
  val context = LocalContext.current
  CaptureDb.load(context)
  var type by remember { mutableStateOf("all") }
  var expanded by remember { mutableStateOf<String?>(null) }

  val rows = CaptureDb.taskRows().filter { type == "all" || it.type == type }
  val pending = rows.filter { !it.task.done }
  val completed = rows.filter { it.task.done }

  Column(Modifier.fillMaxSize().padding(bottom = bottomPadding).padding(horizontal = 20.dp)) {
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("Tasks", style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink, modifier = Modifier.weight(1f))
      Meta("${pending.size} pending")
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TASK_TYPES.forEach { (key, label) ->
        val selected = type == key
        Text(
          label,
          style = MaterialTheme.typography.labelMedium,
          color = if (selected) CaptureColors.Paper else CaptureColors.Neutral,
          modifier =
            Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (selected) CaptureColors.Navy else CaptureColors.Hairline)
              .clickable { type = key }
              .padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
      if (rows.isEmpty()) {
        EmptyState("No tasks here yet — record a note, or scan a meeting QR.")
      } else {
        pending.forEach { row ->
          TaskCheckRow(
            row = row,
            expanded = expanded == row.task.id,
            onToggleDone = { done -> CaptureDb.setTaskDoneByRow(context, row, done) },
            onExpandToggle = { expanded = if (expanded == row.task.id) null else row.task.id },
          )
          Hairline()
        }
        if (completed.isNotEmpty()) {
          Spacer(Modifier.height(20.dp))
          Text("Completed", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
          Spacer(Modifier.height(4.dp))
          completed.forEach { row ->
            TaskCheckRow(
              row = row,
              expanded = expanded == row.task.id,
              onToggleDone = { done -> CaptureDb.setTaskDoneByRow(context, row, done) },
              onExpandToggle = { expanded = if (expanded == row.task.id) null else row.task.id },
            )
            Hairline()
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun TaskCheckRow(
  row: TaskRow,
  expanded: Boolean,
  onToggleDone: (Boolean) -> Unit,
  onExpandToggle: () -> Unit,
) {
  val done = row.task.done
  RailRow(railColor = priorityColor(row.task.priority)) {
    Row(
      Modifier.fillMaxWidth().clickable { onExpandToggle() },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // The checkbox toggles done; tapping anywhere else opens the detail.
      Box(Modifier.clickable { onToggleDone(!done) }) { CheckBoxSquare(done) }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
          row.task.title,
          style = MaterialTheme.typography.bodyLarge,
          color = if (done) CaptureColors.Neutral else CaptureColors.Ink,
          textDecoration = if (done) TextDecoration.LineThrough else null,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          TaskSourceTag(row.type)
          Spacer(Modifier.width(10.dp))
          PriorityTag(row.task.priority)
          if (row.task.dueDate.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text("Due ${row.task.dueDate}", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(
          if (row.task.owner.isNotBlank()) "For ${row.task.owner}" else "Unassigned",
          style = MaterialTheme.typography.bodySmall,
          color = if (row.task.owner.isNotBlank()) CaptureColors.Ink else CaptureColors.Neutral,
        )
      }
    }
    if (expanded) {
      Spacer(Modifier.height(12.dp))
      Hairline()
      Spacer(Modifier.height(12.dp))
      TaskDetail(row)
    }
  }
}

/** Dropdown body: the task, who it is for, the photo (if any) and the transcript it came from. */
@Composable
private fun TaskDetail(row: TaskRow) {
  val context = LocalContext.current
  CaptureDb.load(context)
  val dicto = if (row.type == "dicto") CaptureDb.state.dicto.firstOrNull { it.id == row.entryId } else null
  val meeting = if (row.type == "qr") CaptureDb.state.meetings.firstOrNull { it.id == row.task.sourceId } else null

  Column(Modifier.fillMaxWidth()) {
    DetailLabel("Task")
    Text(row.task.title, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(10.dp))
    DetailLabel("Assigned to")
    Text(
      if (row.task.owner.isNotBlank()) row.task.owner else "Unassigned",
      style = MaterialTheme.typography.bodyMedium,
      color = CaptureColors.Ink,
    )
    Spacer(Modifier.height(10.dp))
    DetailLabel("Priority and due")
    Row(verticalAlignment = Alignment.CenterVertically) {
      PriorityTag(row.task.priority)
      if (row.task.dueDate.isNotBlank()) {
        Spacer(Modifier.width(10.dp))
        Text("Due ${row.task.dueDate}", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
      }
    }

    val imagePath = dicto?.imagePath
    if (!imagePath.isNullOrBlank()) {
      Spacer(Modifier.height(12.dp))
      DetailLabel("Photo")
      val bmp = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
      if (bmp != null) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
        )
      }
    }

    val formatted = dicto?.formatted.orEmpty()
    if (formatted.isNotBlank()) {
      Spacer(Modifier.height(12.dp))
      DetailLabel("Formatted note")
      Text(formatted, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
    }

    val summary = meeting?.summary.orEmpty()
    if (summary.isNotBlank()) {
      Spacer(Modifier.height(12.dp))
      DetailLabel("Meeting summary")
      Text(summary, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
    }

    val transcript = dicto?.transcript?.ifBlank { null } ?: meeting?.transcript?.ifBlank { null }
    if (!transcript.isNullOrBlank()) {
      Spacer(Modifier.height(12.dp))
      DetailLabel(if (row.type == "qr") "Meeting transcript" else "Transcript")
      Text(transcript, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
    } else if (row.type == "qr") {
      Spacer(Modifier.height(8.dp))
      Meta("Transcript not carried in the QR — the meeting summary above is what this device has.")
    }
    Spacer(Modifier.height(4.dp))
  }
}

@Composable
private fun DetailLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = CaptureColors.Neutral,
    modifier = Modifier.padding(bottom = 2.dp),
  )
}

@Composable
private fun TaskSourceTag(type: String) {
  Text(
    if (type == "qr") "QR" else "Dictation",
    style = MaterialTheme.typography.labelMedium,
    color = CaptureColors.Neutral,
  )
}
