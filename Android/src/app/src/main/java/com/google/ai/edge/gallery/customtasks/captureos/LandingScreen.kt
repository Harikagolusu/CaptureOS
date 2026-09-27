package com.google.ai.edge.gallery.customtasks.captureos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val SWIPE_THRESHOLD_DP = 130f

private sealed interface LandingStage {
  data object Home : LandingStage
  data object MeetingRecording : LandingStage
  data class MeetingProcessing(val step: Int, val meta: String) : LandingStage
  data class MeetingDone(val transcript: String, val archive: MeetingArchive) : LandingStage
  data object DictoRecording : LandingStage
  data class DictoProcessing(val step: Int, val meta: String) : LandingStage
  data class DictoDone(val entry: DictoEntry) : LandingStage
  data class Failed(val message: String) : LandingStage
}

/**
 * Landing/home page: the capture button is the hero. Swipe up to record a meeting inline,
 * swipe down to dictate inline — no page switch, it all happens right here.
 */
@Composable
fun LandingScreen(profile: SetupProfile, model: Model?, onOpenMeeting: () -> Unit, onOpenDictation: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val recorder = remember { MeetingRecorder() }

  var stage by remember { mutableStateOf<LandingStage>(LandingStage.Home) }
  var seconds by remember { mutableIntStateOf(0) }

  val permissions =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

  fun ensureAudioPermission(): Boolean {
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    if (!granted) permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    return granted
  }

  fun startMeetingRecording() {
    if (!ensureAudioPermission()) return
    recorder.start()
    stage = LandingStage.MeetingRecording
  }

  fun startDictoRecording() {
    if (!ensureAudioPermission()) return
    recorder.start()
    stage = LandingStage.DictoRecording
  }

  fun processMeeting(clips: List<ByteArray>) {
    if (model == null) {
      stage = LandingStage.Failed("Gemma model is still loading on your phone…")
      return
    }
    stage = LandingStage.MeetingProcessing(0, "")
    scope.launch {
      try {
        var transcript = ""
        val meeting =
          withContext(Dispatchers.Default) {
            val p = CapturePipeline(context, model)
            transcript = p.transcribe(clips) { d, t -> stage = LandingStage.MeetingProcessing(0, "part ${d + 1} of $t") }
            stage = LandingStage.MeetingProcessing(1, "")
            p.extractMeeting(transcript, profile.teamCode, "")
          }
        val id = UUID.randomUUID().toString()
        val archive =
          MeetingArchive(
            id = id,
            teamCode = meeting.teamCode,
            meetingTitle = meeting.meetingTitle.ifBlank { "Meeting ${System.currentTimeMillis() / 1000}" },
            hostName = profile.name,
            status = "recorded",
            transcript = transcript,
            summary = meeting.summary,
            decisions = meeting.decisions,
            actionItems = meeting.actionItems,
          )
        CaptureDb.upsertMeeting(context, archive)
        CaptureDb.log(context, "meeting", "Recorded ${archive.meetingTitle} · ${meeting.actionItems.size} tasks")
        meeting.actionItems.forEach { a ->
          val at = parseDeadlineForLanding(a.deadline) ?: return@forEach
          val event =
            CalendarEvent(
              title = a.task,
              kind = "task",
              startAt = at,
              priority = a.priority,
              owner = a.owner,
              sourceId = archive.id,
            )
          val calendarId = CalendarWriter.write(context, event)
          CaptureDb.addEvent(context, event.copy(calendarEventId = calendarId))
          ReminderChain.schedule(context, event)
        }
        stage = LandingStage.MeetingDone(transcript, archive)
      } catch (e: Exception) {
        stage = LandingStage.Failed(e.message ?: "Something went wrong")
      }
    }
  }

  fun processDicto(clips: List<ByteArray>) {
    if (model == null) {
      stage = LandingStage.Failed("Gemma model is still loading on your phone…")
      return
    }
    stage = LandingStage.DictoProcessing(0, "")
    scope.launch {
      try {
        var transcript = ""
        val result =
          withContext(Dispatchers.Default) {
            val p = CapturePipeline(context, model)
            transcript = p.transcribe(clips) { d, t -> stage = LandingStage.DictoProcessing(0, "part ${d + 1} of $t") }
            stage = LandingStage.DictoProcessing(1, "")
            p.extractDicto(transcript, null)
          }
        val id = UUID.randomUUID().toString()
        val audioFile = File(CaptureDb.audioDir(context), "$id.wav")
        runCatching { audioFile.writeBytes(toWavBytes(recorder.lastPcm)) }
        val entry =
          DictoEntry(
            id = id,
            transcript = transcript,
            audioPath = audioFile.absolutePath.takeIf { audioFile.exists() },
            categories = result.categories,
            keyPoints = result.keyPoints,
            formatted = result.formatted,
            tasks = result.tasks,
          )
        CaptureDb.addDicto(context, entry)
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
        stage = LandingStage.DictoDone(entry)
      } catch (e: Exception) {
        stage = LandingStage.Failed(e.message ?: "Something went wrong")
      }
    }
  }

  fun stopRecording() {
    val clips = recorder.stop()
    when (stage) {
      LandingStage.MeetingRecording -> processMeeting(clips)
      LandingStage.DictoRecording -> processDicto(clips)
      else -> {}
    }
  }

  LaunchedEffect(stage) {
    while (stage == LandingStage.MeetingRecording || stage == LandingStage.DictoRecording) {
      seconds = recorder.seconds
      delay(200)
    }
  }

  Box(Modifier.fillMaxSize().background(CaptureColors.Paper)) {
    when (val s = stage) {
      LandingStage.Home ->
        HomeLanding(
          onSwipeUp = ::startMeetingRecording,
          onSwipeDown = ::startDictoRecording,
        )
      LandingStage.MeetingRecording ->
        RecordingLanding(
          mode = "meeting",
          seconds = seconds,
          onStop = ::stopRecording,
          onBack = {
            recorder.cancel()
            seconds = 0
            stage = LandingStage.Home
          },
        )
      LandingStage.DictoRecording ->
        RecordingLanding(
          mode = "dicto",
          seconds = seconds,
          onStop = ::stopRecording,
          onBack = {
            recorder.cancel()
            seconds = 0
            stage = LandingStage.Home
          },
        )
      is LandingStage.MeetingProcessing -> ProcessingLanding(mode = "meeting", step = s.step, meta = s.meta)
      is LandingStage.DictoProcessing -> ProcessingLanding(mode = "dicto", step = s.step, meta = s.meta)
      is LandingStage.MeetingDone -> MeetingDoneLanding(done = s, onHome = { seconds = 0; stage = LandingStage.Home })
      is LandingStage.DictoDone -> DictoDoneLanding(entry = s.entry, onHome = { seconds = 0; stage = LandingStage.Home })
      is LandingStage.Failed ->
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Spacer(Modifier.height(60.dp))
          ScreenTitle("Couldn't record")
          Spacer(Modifier.height(10.dp))
          Text(s.message, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral, textAlign = TextAlign.Center)
          Spacer(Modifier.height(28.dp))
          PrimaryButton("Back", onClick = { stage = LandingStage.Home })
        }
    }
  }
}

// ---------------- home (idle) ----------------

@Composable
private fun HomeLanding(onSwipeUp: () -> Unit, onSwipeDown: () -> Unit) {
  val scope = rememberCoroutineScope()
  val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }
  var dragY by remember { mutableFloatStateOf(0f) }
  var direction by remember { mutableIntStateOf(0) }

  val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }

  val transition = rememberInfiniteTransition(label = "capturePulse")
  val pulse by
    transition.animateFloat(
      initialValue = 1f,
      targetValue = 1.08f,
      animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
      label = "pulse",
    )
  val glowAlpha by
    transition.animateFloat(
      initialValue = 0.35f,
      targetValue = 0.14f,
      animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
      label = "glow",
    )

  val progress = (dragY.absoluteValue / thresholdPx).coerceIn(0f, 1f)

  Box(
    Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(
            CaptureColors.Navy.copy(alpha = 0.05f),
            CaptureColors.Paper,
            CaptureColors.Navy.copy(alpha = 0.05f),
          ),
        )
      )
      .pointerInput(Unit) {
        detectVerticalDragGestures(
          onDragStart = { direction = 0 },
          onDragEnd = {
            if (direction != 0 && dragY.absoluteValue >= thresholdPx) {
              if (direction < 0) onSwipeUp() else onSwipeDown()
            }
            dragY = 0f
            direction = 0
            scope.launch { offsetY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
          },
          onDragCancel = {
            dragY = 0f
            direction = 0
            scope.launch { offsetY.snapTo(0f) }
          },
        ) { _, dragAmount ->
          dragY += dragAmount
          direction =
            when {
              dragY < -24.dp.toPx() -> -1
              dragY > 24.dp.toPx() -> 1
              else -> 0
            }
          scope.launch { offsetY.snapTo((dragY * 0.6f).coerceIn(-80f, 80f)) }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Wordmark()
        Spacer(Modifier.height(6.dp))
        Text("Your voice, captured.", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
      }

      SwipeHint(
        icon = Icons.Filled.KeyboardArrowUp,
        label = "Swipe up — Meeting",
        highlighted = direction < 0,
        progress = if (direction < 0) progress else 0f,
      )
      Spacer(Modifier.height(20.dp))

      Box(
        modifier =
          Modifier
            .size(190.dp, 190.dp)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
              val s = pulse * (1f + progress * 0.06f)
              scaleX = s
              scaleY = s
            },
        contentAlignment = Alignment.Center,
      ) {
        val strokePx = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
          val inset = strokePx
          drawArc(
            color = CaptureColors.Navy.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokePx),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
          )
          drawArc(
            color = CaptureColors.Navy,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
          )
        }
        Box(
          Modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(CaptureColors.Navy.copy(alpha = glowAlpha + progress * 0.15f)),
        )
        Box(
          Modifier
            .size(155.dp)
            .clip(CircleShape)
            .background(CaptureColors.Navy)
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = CaptureColors.Paper, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(8.dp))
            Text(
              "CAPTURE",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
              color = CaptureColors.Paper,
            )
          }
        }
      }

      Spacer(Modifier.height(20.dp))

      SwipeHint(
        icon = Icons.Filled.KeyboardArrowDown,
        label = "Swipe down — Dictation",
        highlighted = direction > 0,
        progress = if (direction > 0) progress else 0f,
      )

      Text(
        "Offline-first · runs fully on your phone",
        style = MaterialTheme.typography.bodySmall,
        color = CaptureColors.Neutral,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp),
      )
    }
  }
}

@Composable
private fun SwipeHint(
  icon: ImageVector,
  label: String,
  highlighted: Boolean,
  progress: Float,
  modifier: Modifier = Modifier,
) {
  val color = if (highlighted) CaptureColors.Navy else CaptureColors.Neutral
  val size = 30.dp + (8 * progress).dp
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size))
    Spacer(Modifier.height(4.dp))
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
      color = color,
    )
  }
}

// ---------------- recording ----------------

@Composable
private fun RecordingLanding(mode: String, seconds: Int, onStop: () -> Unit, onBack: () -> Unit) {
  val isMeeting = mode == "meeting"
  val accent = if (isMeeting) CaptureColors.High else CaptureColors.Navy

  val transition = rememberInfiniteTransition(label = "recordPulse")
  val pulse by
    transition.animateFloat(
      initialValue = 1f,
      targetValue = 1.1f,
      animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
      label = "pulse",
    )

  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.08f), CaptureColors.Paper)))) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
          Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = CaptureColors.Neutral)
        }
        Spacer(Modifier.weight(1f))
        Text(if (isMeeting) "Meeting Mode" else "Dictation", style = MaterialTheme.typography.titleLarge, color = accent)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
      }
      Spacer(Modifier.height(40.dp))
      Box(Modifier.size(130.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(CaptureColors.Navy.copy(alpha = 0.10f)))
        Icon(Icons.Filled.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(56.dp))
      }
      Spacer(Modifier.height(20.dp))
      Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineLarge, color = CaptureColors.Ink)
      Spacer(Modifier.height(8.dp))
      Text("Listening… tap stop when you're done", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
      Spacer(Modifier.weight(1f))
      IconButton(
        onClick = onStop,
        modifier = Modifier.size(84.dp).clip(CircleShape).background(accent),
      ) {
        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = CaptureColors.Paper, modifier = Modifier.size(34.dp))
      }
      Spacer(Modifier.height(40.dp))
    }
  }
}

// ---------------- processing ----------------

@Composable
private fun ProcessingLanding(mode: String, step: Int, meta: String) {
  val isMeeting = mode == "meeting"
  Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
    Spacer(Modifier.height(48.dp))
    Wordmark()
    Spacer(Modifier.height(28.dp))
    ScreenTitle(if (isMeeting) "Processing meeting…" else "Processing dictation…")
    Spacer(Modifier.height(26.dp))
    LoadingSteps(
      steps =
        if (isMeeting) listOf("Transcribing…", "Extracting decisions and tasks…")
        else listOf("Transcribing…", "Sorting into buckets…"),
      currentIndex = step,
    )
    if (meta.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      Meta(meta)
    }
  }
}

// ---------------- results ----------------

@Composable
private fun MeetingDoneLanding(done: LandingStage.MeetingDone, onHome: () -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
    Spacer(Modifier.height(24.dp))
    Text("Saved ✓", style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(4.dp))
    Text(done.archive.meetingTitle, style = MaterialTheme.typography.titleLarge, color = CaptureColors.Navy)
    Spacer(Modifier.height(2.dp))
    Meta("${done.archive.actionItems.size} action items · ${done.archive.decisions.size} decisions")

    if (done.archive.summary.isNotBlank()) {
      Spacer(Modifier.height(18.dp))
      Text("Summary", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
      Spacer(Modifier.height(6.dp))
      Text(done.archive.summary, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
    }

    if (done.archive.decisions.isNotEmpty()) {
      Spacer(Modifier.height(18.dp))
      Text("Decisions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
      Spacer(Modifier.height(6.dp))
      done.archive.decisions.forEach { d ->
        Text("— $d", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
        Spacer(Modifier.height(4.dp))
      }
    }

    if (done.archive.actionItems.isNotEmpty()) {
      Spacer(Modifier.height(18.dp))
      Text("Work", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
      Spacer(Modifier.height(6.dp))
      done.archive.actionItems.forEach { a ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
          Icon(
            if (a.ownerConfidence == "named") Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = priorityColor(a.priority),
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
          )
          Spacer(Modifier.width(10.dp))
          Column {
            Text(a.task, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = CaptureColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text(
              "${a.owner.ifBlank { "Unassigned" }} · ${a.priority.capitalizeFirst()}${if (!a.deadline.isNullOrBlank()) " · ${a.deadline}" else ""}",
              style = MaterialTheme.typography.bodySmall,
              color = CaptureColors.Neutral,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(28.dp))
    PrimaryButton("Done", onClick = onHome)
  }
}

@Composable
private fun DictoDoneLanding(entry: DictoEntry, onHome: () -> Unit) {
  val context = LocalContext.current
  var assignTaskId by remember { mutableStateOf<String?>(null) }
  var assignName by remember { mutableStateOf("") }
  var qrNote by remember { mutableStateOf<TaskCode?>(null) }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
    Spacer(Modifier.height(24.dp))
    Text("Saved ✓", style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(8.dp))
    val summary =
      buildString {
        append(if (entry.categories.size == 1) "1 bucket" else "${entry.categories.size} buckets")
        append(" · ")
        append(if (entry.tasks.size == 1) "1 task" else "${entry.tasks.size} tasks")
        if (entry.keyPoints.isNotEmpty()) append(" · ${entry.keyPoints.size} points")
      }
    Text(summary, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
    if (entry.formatted.isNotBlank()) {
      Spacer(Modifier.height(12.dp))
      Text(entry.formatted, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
    }

    if (entry.tasks.isNotEmpty()) {
      Spacer(Modifier.height(20.dp))
      Text("Tasks found — assign & share", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
      Spacer(Modifier.height(4.dp))
      Text("Type a name to assign each task, then share its QR so the person can scan and accept it.", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
      Spacer(Modifier.height(8.dp))
      entry.tasks.forEachIndexed { index, task ->
        RailRow(railColor = priorityColor(task.priority)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.  ", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Navy)
            Column(Modifier.weight(1f)) {
              Text(task.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = CaptureColors.Ink)
              Spacer(Modifier.height(2.dp))
              Text(
                "${if (task.owner.isNotBlank()) task.owner else "Unassigned"} · ${task.priority.capitalizeFirst()}${if (task.dueDate.isNotBlank()) " · Due ${task.dueDate}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = CaptureColors.Neutral,
              )
            }
            if (task.owner.isBlank()) {
              TextButton(onClick = {
                assignTaskId = task.id
                assignName = ""
                qrNote = null
              }) {
                Text("Assign", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
              }
            } else {
              TextButton(onClick = {
                qrNote =
                  TaskCode(
                    title = task.title,
                    owner = task.owner,
                    priority = task.priority,
                    due = task.dueDate,
                  )
                assignTaskId = null
              }) {
                Text("QR", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Low)
              }
            }
          }
        }
        Hairline()
      }
    }

    if (qrNote != null) {
      Spacer(Modifier.height(20.dp))
      Text("Task QR — share with the assignee", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
      Spacer(Modifier.height(4.dp))
      Text("${qrNote!!.title} · ${qrNote!!.owner}", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
      Spacer(Modifier.height(10.dp))
      val bmp = remember(qrNote) { runCatching { TaskQrCodec.taskToQr(qrNote!!) }.getOrNull() }
      if (bmp != null) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "Task QR",
          modifier =
            Modifier
              .fillMaxWidth()
              .height(240.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(CaptureColors.Paper),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          TextButton(onClick = {
            val uri = QrCodec.saveToDownloads(context, bmp, "captureos_task_${System.currentTimeMillis()}.png")
            if (uri != null) context.startActivity(Intent.createChooser(QrCodec.shareIntent(uri), "Share task QR"))
          }) {
            Text("Save & share", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
          }
          TextButton(onClick = { qrNote = null }) {
            Text("Close", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
          }
        }
      }
    }

    Spacer(Modifier.height(28.dp))
    PrimaryButton("Done", onClick = onHome)
  }

  if (assignTaskId != null) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { assignTaskId = null },
      title = { Text("Assign task") },
      text = {
        Column {
          Text("Who should take this task?", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = assignName,
            onValueChange = {
              assignName = it
              qrNote = null
            },
            singleLine = true,
            placeholder = { Text("Person's name", color = CaptureColors.Neutral) },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CaptureColors.Navy, unfocusedBorderColor = CaptureColors.Hairline),
          )
        }
      },
      confirmButton = {
        androidx.compose.material3.TextButton(
          onClick = {
            val id = assignTaskId
            val task = entry.tasks.firstOrNull { it.id == id }
            if (id != null && task != null && assignName.isNotBlank()) {
              CaptureDb.setTaskOwner(context, entry.id, id, assignName.trim())
              CaptureDb.log(context, "assign", "Assigned “${task.title}” to ${assignName.trim()}")
            }
            if (id != null && task != null && assignName.isNotBlank()) {
              qrNote =
                TaskCode(
                  title = task.title,
                  owner = assignName.trim(),
                  priority = task.priority,
                  due = task.dueDate,
                )
            }
            assignTaskId = null
            assignName = ""
          },
          enabled = assignName.isNotBlank(),
        ) {
          Text("Assign & make QR", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
        }
      },
      dismissButton = {
        androidx.compose.material3.TextButton(onClick = { assignTaskId = null }) {
          Text("Cancel", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
        }
      },
    )
  }
}

private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercase() }

private fun parseDeadlineForLanding(raw: String?): Long? {
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