package com.google.ai.edge.gallery.customtasks.captureos

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
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
fun EmployeeScreen(profile: SetupProfile, bottomPadding: androidx.compose.ui.unit.Dp) {
  val context = LocalContext.current
  var stage by remember { mutableStateOf<EmployeeStage>(EmployeeStage.Scanning) }
  var handled by remember { mutableStateOf(false) }
  val scanner = remember { BarcodeScanning.getClient() }

  when (val s = stage) {
    is EmployeeStage.Result ->
      ResultView(meeting = s.meeting, mine = s.mine, name = profile.name, onScanAgain = {
        handled = false
        stage = EmployeeStage.Scanning
      }, bottomPadding = bottomPadding)

    else -> {
      Column(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        Text(
          "Employee · Scan Meeting QR",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
        if (s is EmployeeStage.Rejected) {
          Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
          Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
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
                      stage = decode(raw, profile)
                    }
                    imageProxy.close()
                  }
                  .addOnFailureListener { imageProxy.close() }
              }
            },
          )
        }
        Text(
          "Point the camera at the admin's QR code",
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
      }
    }
  }
}

private fun decode(raw: String, profile: SetupProfile): EmployeeStage {
  val meeting =
    runCatching { parseMeetingJson(raw) }.getOrElse {
      return EmployeeStage.Rejected("That QR is not a CaptureOS meeting code.")
    }
  if (meeting.teamCode != profile.teamCode) {
    return EmployeeStage.Rejected("Different team (${meeting.teamCode}). This QR is not for team ${profile.teamCode}.")
  }
  val mine = meeting.actionItems.filter { it.owner.trim().equals(profile.name.trim(), ignoreCase = true) }
  return EmployeeStage.Result(meeting, mine)
}

@Composable
private fun ResultView(
  meeting: MeetingRecord,
  mine: List<ActionItem>,
  name: String,
  onScanAgain: () -> Unit,
  bottomPadding: androidx.compose.ui.unit.Dp,
) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(16.dp),
  ) {
    Text(meeting.meetingTitle, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(meeting.summary)
    if (meeting.decisions.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      Text("Decisions", fontWeight = FontWeight.Bold)
      meeting.decisions.forEach { Text("• $it") }
    }
    Spacer(Modifier.height(20.dp))
    Text("Your tasks, $name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (mine.isEmpty()) {
      Text("No tasks assigned to you from this meeting.")
    } else {
      mine.forEach { ActionCard(it) }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text("Scan another") }
  }
}

@Composable
private fun ActionCard(item: ActionItem) {
  Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Column(Modifier.padding(12.dp)) {
      Text(item.task, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag(priorityLabel(item.priority), priorityColor(item.priority))
        Tag(confidenceLabel(item.ownerConfidence), confidenceColor(item.ownerConfidence))
      }
      Spacer(Modifier.height(6.dp))
      Text(
        buildString {
          append("Owner: ${item.owner}")
          item.deadline?.let { append(" · Due: $it") }
        },
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun Tag(text: String, color: Color) {
  Box(
    Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
  }
}

private fun priorityLabel(p: String) =
  when (p.lowercase()) {
    "high" -> "🔴 High"
    "low" -> "🟢 Low"
    else -> "🟡 Medium"
  }

private fun priorityColor(p: String) =
  when (p.lowercase()) {
    "high" -> Color(0xFFD32F2F)
    "low" -> Color(0xFF388E3C)
    else -> Color(0xFFF9A825)
  }

private fun confidenceLabel(c: String) =
  when (c.lowercase()) {
    "named" -> "🟢 named"
    "inferred" -> "🟡 inferred"
    else -> "🔴 unassigned"
  }

private fun confidenceColor(c: String) =
  when (c.lowercase()) {
    "named" -> Color(0xFF388E3C)
    "inferred" -> Color(0xFFF9A825)
    else -> Color(0xFFD32F2F)
  }