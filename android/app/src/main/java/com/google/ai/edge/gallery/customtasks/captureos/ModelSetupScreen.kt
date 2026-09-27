package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType

/**
 * First-run model setup, in the CaptureOS design language. Replaces the Gallery download panel:
 * one navy action, plain instructive copy, no chrome.
 */
@Composable
fun ModelSetupScreen(
  displayName: String,
  status: ModelDownloadStatus?,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
) {
  val type = status?.status ?: ModelDownloadStatusType.NOT_DOWNLOADED
  val downloading = type == ModelDownloadStatusType.IN_PROGRESS || type == ModelDownloadStatusType.UNZIPPING
  val failed = type == ModelDownloadStatusType.FAILED
  val progress =
    if (status != null && status.totalBytes > 0) (status.receivedBytes.toFloat() / status.totalBytes).coerceIn(0f, 1f)
    else 0f

  Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    Spacer(Modifier.height(32.dp))
    Wordmark()
    Spacer(Modifier.height(6.dp))
    Text("One phone. Two outputs.", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)

    Spacer(Modifier.height(36.dp))
    ScreenTitle("Set up CaptureOS")
    Spacer(Modifier.height(10.dp))
    Text(
      "CaptureOS runs Gemma on this phone. Download it once — after that recording, transcription " +
        "and task extraction all work offline.",
      style = MaterialTheme.typography.bodyLarge,
      color = CaptureColors.Ink,
    )

    Spacer(Modifier.height(24.dp))
    Hairline()
    Spacer(Modifier.height(16.dp))
    Text(displayName, style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(2.dp))
    Text(
      when {
        downloading -> "Downloading… ${(progress * 100).toInt()}%  ·  ${formatBytes(status?.receivedBytes ?: 0)} of ${formatBytes(status?.totalBytes ?: 0)}"
        failed -> status?.errorMessage?.ifBlank { "Download failed" } ?: "Download failed"
        type == ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> "Partly downloaded — resume to finish."
        else -> "About ${formatBytes(status?.totalBytes ?: 0)} · runs entirely on this device"
      },
      style = MaterialTheme.typography.bodySmall,
      color = if (failed) CaptureColors.High else CaptureColors.Neutral,
    )
    if (downloading) {
      Spacer(Modifier.height(12.dp))
      LinearProgressIndicator(
        progress = { progress },
        color = CaptureColors.Navy,
        trackColor = CaptureColors.Hairline,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    Spacer(Modifier.height(28.dp))
    PrimaryButton(
      text =
        when {
          downloading -> "Downloading…"
          failed -> "Try download again"
          type == ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> "Resume download"
          else -> "Download Gemma"
        },
      onClick = onDownload,
      enabled = !downloading,
    )
    if (downloading) {
      Spacer(Modifier.height(8.dp))
      Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = onCancel, modifier = Modifier.width(160.dp)) {
          Text("Cancel download", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
        }
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "—"
  val gb = bytes / 1_000_000_000.0
  if (gb >= 1) return "%.2f GB".format(gb)
  val mb = bytes / 1_000_000.0
  return "%.0f MB".format(mb)
}
