package com.google.ai.edge.gallery.customtasks.captureos

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.LiveCameraView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.UUID

private sealed interface CheckInStage {
  data object NameEntry : CheckInStage
  data object Scanning : CheckInStage
  data class Result(val meeting: MeetingRecord, val assigned: List<ActionItem>) : CheckInStage
  data class Error(val message: String) : CheckInStage
}

/**
 * Scan a meeting QR to check in. Asks for the person's name first, then matches it against the
 * meeting's action items. Matches become pending tasks on the Tasks page under "QR".
 */
@Composable
fun MeetingScanScreen(onClose: () -> Unit) {
  val context = LocalContext.current
  var typedName by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var stage by remember { mutableStateOf<CheckInStage>(CheckInStage.NameEntry) }
  var handled by remember { mutableStateOf(false) }
  val scanner = remember { BarcodeScanning.getClient() }

  // System back always leaves the scanner instead of getting stuck.
  BackHandler { onClose() }

  when (val s = stage) {
    is CheckInStage.NameEntry ->
      Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenTitle("Scan meeting QR")
        Spacer(Modifier.height(6.dp))
        Text(
          "Enter your name so we can match the tasks that are yours.",
          style = MaterialTheme.typography.bodyMedium,
          color = CaptureColors.Neutral,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
          value = typedName,
          onValueChange = { typedName = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = { Text("Your name", color = CaptureColors.Neutral) },
          shape = RoundedCornerShape(8.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CaptureColors.Navy,
              unfocusedBorderColor = CaptureColors.Hairline,
              cursorColor = CaptureColors.Navy,
            ),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
          "Continue",
          onClick = {
            name = typedName.trim()
            handled = false
            stage = CheckInStage.Scanning
          },
          enabled = typedName.isNotBlank(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) {
          Text("Cancel", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
        }
      }

    is CheckInStage.Scanning ->
      Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
          Spacer(Modifier.height(24.dp))
          ScreenTitle("Scan meeting QR")
          Spacer(Modifier.height(4.dp))
          Meta("Checking in as $name")
          Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
          LiveCameraView(
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            isPaused = handled,
            modifier = Modifier.fillMaxSize(),
            onBitmap = { bitmap: Bitmap, imageProxy ->
              if (handled) {
                imageProxy.close()
              } else {
                scanner
                  .process(InputImage.fromBitmap(bitmap, 0))
                  .addOnSuccessListener { barcodes ->
                    val raw = barcodes.firstNotNullOfOrNull { b: Barcode -> b.rawValue }
                    if (raw != null && !handled) {
                      handled = true
                      stage = checkIn(context, raw, name)
                    }
                    imageProxy.close()
                  }
                  .addOnFailureListener { imageProxy.close() }
              }
            },
          )
          ScanReticle(Modifier.align(Alignment.Center))
        }
        Text(
          "Point the camera at the host's QR code",
          style = MaterialTheme.typography.bodySmall,
          color = CaptureColors.Neutral,
          modifier = Modifier.padding(20.dp),
        )
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
          TextButton(onClick = onClose) {
            Text("Close scanner", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
          }
        }
      }

    is CheckInStage.Result ->
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      ) {
        Spacer(Modifier.height(24.dp))
        ScreenTitle(s.meeting.meetingTitle)
        Spacer(Modifier.height(6.dp))
        Text("Checked in as $name", style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
        Spacer(Modifier.height(20.dp))
        Text("Assigned to you", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
        Spacer(Modifier.height(4.dp))
        s.assigned.forEach { item ->
          RailRow(railColor = priorityColor(item.priority)) {
            PriorityTag(item.priority)
            Spacer(Modifier.height(6.dp))
            Text(item.task, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
            if (!item.deadline.isNullOrBlank()) {
              Spacer(Modifier.height(2.dp))
              Text("Due ${item.deadline}", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
            }
          }
          Hairline()
        }
        Spacer(Modifier.height(12.dp))
        Meta("These are now on your Tasks page under “QR”.")
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Done", onClick = onClose)
        Spacer(Modifier.height(16.dp))
      }

    is CheckInStage.Error ->
      Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenTitle("Couldn't check in")
        Spacer(Modifier.height(10.dp))
        Text(s.message, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.High)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Scan again", onClick = {
          handled = false
          stage = CheckInStage.Scanning
        })
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { stage = CheckInStage.NameEntry }) {
          Text("Change name", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onClose) {
          Text("Cancel", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
        }
      }
  }
}

/** Decodes the QR and matches the entered name against the meeting roster. */
private fun checkIn(context: android.content.Context, raw: String, name: String): CheckInStage {
  val meeting =
    runCatching { parseMeetingJson(raw) }.getOrElse {
      return CheckInStage.Error("That QR is not a CaptureOS meeting code.")
    }
  val mine = meeting.actionItems.filter { it.owner.trim().equals(name.trim(), ignoreCase = true) }
  if (mine.isEmpty()) {
    return CheckInStage.Error("Name not present")
  }
  val meetingId = UUID.randomUUID().toString()
  CaptureDb.addMeeting(
    context,
    MeetingArchive(
      id = meetingId,
      teamCode = meeting.teamCode,
      meetingTitle = meeting.meetingTitle,
      transcript = "",
      summary = meeting.summary,
      decisions = meeting.decisions,
      actionItems = meeting.actionItems,
    ),
  )
  mine.forEach { item ->
    CaptureDb.addQrTask(
      context,
      CapturedTask(
        title = item.task,
        owner = name.trim(),
        dueDate = item.deadline.orEmpty(),
        priority = item.priority,
        source = "qr",
        sourceId = meetingId,
      ),
    )
  }
  CaptureDb.log(context, "check-in", "$name checked in to ${meeting.meetingTitle} · ${mine.size} tasks")
  return CheckInStage.Result(meeting, mine)
}
