package com.google.ai.edge.gallery.customtasks.captureos

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.LiveCameraView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

private sealed interface EmployeeStage {
  data object Scanning : EmployeeStage
  data class Result(val meeting: MeetingRecord, val mine: List<ActionItem>) : EmployeeStage
  data class Rejected(val message: String) : EmployeeStage
}

@Composable
fun EmployeeScreen(profile: SetupProfile, bottomPadding: Dp) {
  var stage by remember { mutableStateOf<EmployeeStage>(EmployeeStage.Scanning) }
  var handled by remember { mutableStateOf(false) }
  val scanner = remember { BarcodeScanning.getClient() }
  val context = LocalContext.current

  when (val s = stage) {
    is EmployeeStage.Result ->
      EmployeeResult(
        meeting = s.meeting,
        mine = s.mine,
        name = profile.name,
        onScanAgain = {
          handled = false
          stage = EmployeeStage.Scanning
        },
        bottomPadding = bottomPadding,
      )
    else -> EmployeeScan(
      rejected = (s as? EmployeeStage.Rejected)?.message,
      handled = handled,
      onBarcode = { raw ->
        if (!handled) {
          handled = true
          val decoded = decode(raw, profile)
          if (decoded is EmployeeStage.Result) {
            CaptureDb.addMeeting(
              context,
              MeetingArchive(
                teamCode = decoded.meeting.teamCode,
                meetingTitle = decoded.meeting.meetingTitle,
                summary = decoded.meeting.summary,
                decisions = decoded.meeting.decisions,
                actionItems = decoded.meeting.actionItems,
              ),
            )
            CaptureDb.log(context, "scan", "Joined ${decoded.meeting.meetingTitle}")
          }
          stage = decoded
        }
      },
      scanner = scanner,
      bottomPadding = bottomPadding,
    )
  }
}

@Composable
private fun EmployeeScan(
  rejected: String?,
  handled: Boolean,
  onBarcode: (String) -> Unit,
  scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
  bottomPadding: Dp,
) {
  Column(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
    Column(Modifier.padding(horizontal = 20.dp)) {
      Spacer(Modifier.height(24.dp))
      ScreenTitle("Scan Meeting QR")
      if (rejected != null) {
        Spacer(Modifier.height(8.dp))
        Text(rejected, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.High)
      }
      Spacer(Modifier.height(16.dp))
    }
    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
      LiveCameraView(
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
        isPaused = handled,
        preferredSize = 1280,
        modifier = Modifier.fillMaxSize(),
        onBitmap = { bitmap: Bitmap, imageProxy ->
          if (handled) {
            imageProxy.close()
          } else {
            scanner
              .process(InputImage.fromBitmap(bitmap, 0))
              .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstNotNullOfOrNull { b: Barcode -> b.rawValue }
                if (raw != null) onBarcode(raw)
                imageProxy.close()
              }
              .addOnFailureListener { imageProxy.close() }
          }
        },
      )
      ScanReticle(Modifier.align(Alignment.Center))
    }
    Text(
      "Point the camera at the admin's QR code",
      style = MaterialTheme.typography.bodySmall,
      color = CaptureColors.Neutral,
      modifier = Modifier.padding(20.dp),
    )
  }
}

/** One moment of motion: a scan line travelling inside the reticle. */
@Composable
fun ScanReticle(modifier: Modifier = Modifier) {
  val size = 240.dp
  val transition = rememberInfiniteTransition(label = "reticle")
  val y by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing), repeatMode = RepeatMode.Restart),
      label = "y",
    )
  Box(
    modifier.size(size).clip(RoundedCornerShape(12.dp)).border(2.dp, CaptureColors.Paper.copy(alpha = 0.9f), RoundedCornerShape(12.dp)),
  ) {
    Box(
      Modifier.fillMaxWidth()
        .height(2.dp)
        .offset(y = (size - 4.dp) * y)
        .background(CaptureColors.Low.copy(alpha = 0.9f))
    )
  }
}

private fun decode(raw: String, profile: SetupProfile): EmployeeStage {
  val meeting =
    runCatching { parseMeetingJson(raw) }.getOrElse {
      return EmployeeStage.Rejected("That QR is not a CaptureOS meeting code.")
    }
  if (meeting.teamCode != profile.teamCode) {
    return EmployeeStage.Rejected("Different team (${meeting.teamCode}) — not for ${profile.teamCode}.")
  }
  val mine = meeting.actionItems.filter { it.owner.trim().equals(profile.name.trim(), ignoreCase = true) }
  return EmployeeStage.Result(meeting, mine)
}

@Composable
private fun EmployeeResult(
  meeting: MeetingRecord,
  mine: List<ActionItem>,
  name: String,
  onScanAgain: () -> Unit,
  bottomPadding: Dp,
) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(horizontal = 20.dp),
  ) {
    Spacer(Modifier.height(24.dp))
    Text(meeting.meetingTitle, style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(8.dp))
    Text(meeting.summary, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)

    if (meeting.decisions.isNotEmpty()) {
      Spacer(Modifier.height(24.dp))
      Text("Decisions", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
      Spacer(Modifier.height(8.dp))
      meeting.decisions.forEach { d ->
        Text("— $d", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink, modifier = Modifier.padding(vertical = 2.dp))
      }
    }

    Spacer(Modifier.height(24.dp))
    Text("Your tasks", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(4.dp))
    if (mine.isEmpty()) {
      EmptyState("No tasks assigned to you from this meeting.")
    } else {
      mine.forEachIndexed { i, item ->
        if (i > 0) Hairline()
        TaskRail(item)
      }
    }

    Spacer(Modifier.height(28.dp))
    PrimaryButton("Scan another", onClick = onScanAgain)
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun TaskRail(item: ActionItem) {
  RailRow(railColor = priorityColor(item.priority)) {
    PriorityTag(item.priority)
    Spacer(Modifier.height(6.dp))
    Text(item.task, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
    if (!item.deadline.isNullOrBlank()) {
      Spacer(Modifier.height(2.dp))
      Text("Due ${item.deadline}", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
    }
    Spacer(Modifier.height(4.dp))
    ConfidenceTag(item.ownerConfidence)
  }
}
