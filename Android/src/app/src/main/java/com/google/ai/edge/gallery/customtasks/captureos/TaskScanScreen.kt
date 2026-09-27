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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.LiveCameraView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.UUID

private sealed interface TaskScanStage {
  data object Scanning : TaskScanStage
  data class Result(val task: TaskCode) : TaskScanStage
  data class Error(val message: String) : TaskScanStage
}

/** Scan a task QR to accept the task. Adds it to the Tasks page under "QR". */
@Composable
fun TaskScanScreen(onClose: () -> Unit) {
  val context = LocalContext.current
  var stage by remember { mutableStateOf<TaskScanStage>(TaskScanStage.Scanning) }
  var handled by remember { mutableStateOf(false) }
  val scanner = remember { BarcodeScanning.getClient() }

  BackHandler { onClose() }

  when (val s = stage) {
    TaskScanStage.Scanning ->
      Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
          Spacer(Modifier.height(24.dp))
          ScreenTitle("Scan a task QR")
          Spacer(Modifier.height(4.dp))
          Meta("Point the camera at the task QR from your manager.")
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
                      stage =
                        runCatching { TaskQrCodec.parseTaskJson(raw) }
                          .fold(
                            onSuccess = { CheckInResultTask(context, it) },
                            onFailure = { TaskScanStage.Error("That QR is not a CaptureOS task code.") },
                          )
                    }
                    imageProxy.close()
                  }
                  .addOnFailureListener { imageProxy.close() }
              }
            },
          )
          ScanReticle(Modifier.align(Alignment.Center))
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
          TextButton(onClick = onClose) {
            Text("Close scanner", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
          }
        }
      }

    is TaskScanStage.Result ->
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenTitle("Task accepted")
        Spacer(Modifier.height(6.dp))
        Text("This task is now on your Tasks page under “QR”.", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
        Spacer(Modifier.height(20.dp))
        RailRow(railColor = priorityColor(s.task.priority)) {
          Text(s.task.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = CaptureColors.Ink)
          Spacer(Modifier.height(4.dp))
          Text(
            buildString {
              append("For ${s.task.owner.ifBlank { "Unassigned" }}")
              append("  ·  ${s.task.priority.capitalizeScan()}")
              if (s.task.due.isNotBlank()) append("  ·  Due ${s.task.due}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = CaptureColors.Neutral,
          )
          if (s.task.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(s.task.note, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
          }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Done", onClick = onClose)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {
          handled = false
          stage = TaskScanStage.Scanning
        }) {
          Text("Scan another", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
        }
        Spacer(Modifier.height(16.dp))
      }

    is TaskScanStage.Error ->
      Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        ScreenTitle("Couldn't read that QR")
        Spacer(Modifier.height(10.dp))
        Text(s.message, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.High)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Scan again", onClick = {
          handled = false
          stage = TaskScanStage.Scanning
        })
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) {
          Text("Cancel", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
        }
      }
  }
}

/** Persists the accepted task into the local QR-task store. */
private fun CheckInResultTask(context: android.content.Context, task: TaskCode): TaskScanStage {
  CaptureDb.addQrTask(
    context,
    CapturedTask(
      title = task.title,
      owner = task.owner,
      dueDate = task.due,
      priority = task.priority,
      source = "qr",
      sourceId = UUID.randomUUID().toString(),
    ),
  )
  CaptureDb.log(context, "task-accept", "Accepted task: ${task.title} · for ${task.owner.ifBlank { "Unassigned" }}")
  return TaskScanStage.Result(task)
}

private fun String.capitalizeScan(): String = replaceFirstChar { it.uppercase() }