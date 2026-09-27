package com.google.ai.edge.gallery.customtasks.captureos

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.LiveCameraView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private sealed interface CheckInStage {
  data object NameEntry : CheckInStage
  data object Scanning : CheckInStage
  data class Result(val meeting: MeetingRecord, val archive: MeetingArchive, val assigned: List<ActionItem>) : CheckInStage
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
            preferredSize = 2048,
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
      ) {
        Spacer(Modifier.height(24.dp))
        ScreenTitle(s.meeting.meetingTitle.ifBlank { "Meeting" })
        Spacer(Modifier.height(4.dp))
        Text("Checked in as $name", style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
        if (s.meeting.hostName.isNotBlank() || s.meeting.recordedAt > 0L) {
          Spacer(Modifier.height(2.dp))
          Text(
            buildString {
              if (s.meeting.hostName.isNotBlank()) append("Host ${s.meeting.hostName}")
              if (s.meeting.recordedAt > 0L) {
                if (s.meeting.hostName.isNotBlank()) append("  ·  ")
                append(SimpleDateFormat("EEE d MMM h:mm a", Locale.getDefault()).format(Date(s.meeting.recordedAt)))
              }
            },
            style = MaterialTheme.typography.bodySmall,
            color = CaptureColors.Neutral,
          )
        }
        Spacer(Modifier.height(6.dp))
        Meta("Team ${s.meeting.teamCode.ifBlank { "—" }} · ${s.meeting.actionItems.size} action items${if (s.meeting.decisions.isEmpty()) "" else " · ${s.meeting.decisions.size} decisions"}")

        // ---- What this meeting is about ----
        if (s.meeting.summary.isNotBlank()) {
          Spacer(Modifier.height(20.dp))
          Text("What this meeting was about", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
          Spacer(Modifier.height(6.dp))
          Text(s.meeting.summary, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
        }

        if (s.meeting.decisions.isNotEmpty()) {
          Spacer(Modifier.height(20.dp))
          Text("Decisions taken", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
          Spacer(Modifier.height(6.dp))
          s.meeting.decisions.forEachIndexed { index, d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
              Text("${index + 1}.  ", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Navy)
              Text(d, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
            }
          }
        }

        // ---- Your tasks (only when the name matches) ----
        if (s.assigned.isNotEmpty()) {
          Spacer(Modifier.height(20.dp))
          Text("Your tasks", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Low)
          Spacer(Modifier.height(4.dp))
          Text("Assigned to you in this meeting:", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
          Spacer(Modifier.height(6.dp))
          s.assigned.forEachIndexed { index, item ->
            RailRow(railColor = item.priority.toColor()) {
              Row(verticalAlignment = Alignment.Top) {
                Text("${index + 1}.  ", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Low)
                Column(Modifier.weight(1f)) {
                  Text(item.task, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Ink)
                  Spacer(Modifier.height(2.dp))
                  Text(
                    buildString {
                      append("Priority: ${item.priority.scanTitleCase()}")
                      if (!item.deadline.isNullOrBlank()) append("  ·  Due: ${item.deadline}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CaptureColors.Neutral,
                  )
                }
              }
            }
            Hairline()
          }
          Spacer(Modifier.height(10.dp))
          Text(
            "These have been added to your Tasks page under “QR”.",
            style = MaterialTheme.typography.bodySmall,
            color = CaptureColors.Neutral,
          )
        }

        // ---- Full action list for everyone ----
        Spacer(Modifier.height(20.dp))
        Text("Everyone's action items", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
        Spacer(Modifier.height(4.dp))
        Text("All tasks from this meeting, whoever owns them:", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
        Spacer(Modifier.height(6.dp))
        if (s.meeting.actionItems.isEmpty()) {
          Text("No action items were captured in this meeting.", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
        } else {
          s.meeting.actionItems.forEachIndexed { index, item ->
            RailRow(railColor = item.priority.toColor()) {
              Row(verticalAlignment = Alignment.Top) {
                Text("${index + 1}.  ", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Navy)
                Column(Modifier.weight(1f)) {
                  Text(item.task, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
                  Spacer(Modifier.height(2.dp))
                  Text(
                    buildString {
                      append(item.owner.ifBlank { "Unassigned" })
                      append("  ·  ${item.priority.scanTitleCase()}")
                      if (!item.deadline.isNullOrBlank()) append("  ·  Due ${item.deadline}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CaptureColors.Neutral,
                  )
                }
              }
            }
            Hairline()
          }
        }
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
  val meetingId = UUID.randomUUID().toString()
  val archive =
    MeetingArchive(
      id = meetingId,
      teamCode = meeting.teamCode,
      meetingTitle = meeting.meetingTitle,
      hostName = meeting.hostName,
      transcript = "",
      summary = meeting.summary,
      decisions = meeting.decisions,
      actionItems = meeting.actionItems,
    )
  CaptureDb.addMeeting(context, archive)
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
  return CheckInStage.Result(meeting, archive, mine)
}

private fun String.toColor(): Color =
  when (trim().lowercase()) {
    "high" -> CaptureColors.High
    "medium" -> CaptureColors.Medium
    "low" -> CaptureColors.Low
    else -> CaptureColors.Neutral
  }

private fun String.scanTitleCase(): String = replaceFirstChar { it.uppercase() }
