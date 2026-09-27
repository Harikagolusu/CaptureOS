package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailDateFormat = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

/**
 * One meeting, fully laid out: description/summary, decisions and every action item (work) with
 * its owner, priority and deadline, then the transcript and shareable QR.
 */
@Composable
fun MeetingDetailScreen(meeting: MeetingArchive, onBack: () -> Unit) {
  val context = LocalContext.current
  var showTranscript by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxSize().background(CaptureColors.Paper)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CaptureColors.Navy)
      }
      Column(Modifier.weight(1f)) {
        Text(
          if (meeting.meetingTitle.isBlank()) "Meeting" else meeting.meetingTitle,
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = CaptureColors.Ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          buildString {
            append(detailDateFormat.format(Date(meeting.createdAt)))
            if (meeting.hostName.isNotBlank()) append("  ·  host ${meeting.hostName}")
            append("  ·  team ${meeting.teamCode}")
          },
          style = MaterialTheme.typography.bodySmall,
          color = CaptureColors.Neutral,
        )
      }
    }
    HorizontalDivider(color = CaptureColors.Hairline)

    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
    ) {
      // Description
      DetailSection(title = "Description") {
        if (meeting.summary.isBlank()) {
          MeetingEmpty("No summary yet — this meeting hasn't been processed.")
        } else {
          Text(meeting.summary, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
        }
      }

      // Decisions
      if (meeting.decisions.isNotEmpty()) {
        DetailSection(title = "Decisions") {
          meeting.decisions.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
              Dot(meeting = true)
              Spacer(Modifier.width(10.dp))
              Text(d, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
            }
          }
        }
      }

      // Work / action items
      DetailSection(title = "Work") {
        if (meeting.actionItems.isEmpty()) {
          MeetingEmpty("No action items captured.")
        } else {
          meeting.actionItems.forEach { item ->
            WorkItemCard(item = item)
            Spacer(Modifier.height(10.dp))
          }
        }
      }

      // Transcript
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Transcript",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = CaptureColors.Ink,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showTranscript = !showTranscript }) {
          Text(if (showTranscript) "Hide" else "Show", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
        }
      }
      if (showTranscript) {
        if (meeting.transcript.isBlank()) {
          MeetingEmpty("No transcript yet.")
        } else {
          Text(
            meeting.transcript,
            style = MaterialTheme.typography.bodyMedium,
            color = CaptureColors.Ink,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          )
        }
      }

      // QR
      val qr =
        remember(meeting.id, meeting.status) {
          if (meeting.status != "recorded") null
          else
            runCatching {
                QrCodec.meetingJsonToQr(
                  MeetingRecord(
                    teamCode = meeting.teamCode,
                    meetingTitle = meeting.meetingTitle,
                    hostName = meeting.hostName,
                    recordedAt = meeting.createdAt,
                    summary = meeting.summary,
                    decisions = meeting.decisions,
                    actionItems = meeting.actionItems,
                  )
                )
              }
              .getOrNull()
        }
      if (qr != null) {
        Spacer(Modifier.height(8.dp))
        Text(
          "Meeting QR",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = CaptureColors.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(CaptureColors.Navy), contentAlignment = Alignment.Center) {
          Image(bitmap = qr.asImageBitmap(), contentDescription = "Meeting QR", modifier = Modifier.fillMaxSize().padding(16.dp))
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = {
          val uri = QrCodec.saveToDownloads(context, qr, "captureos_${meeting.id.take(8)}.png")
          if (uri != null) context.startActivity(Intent.createChooser(QrCodec.shareIntent(uri), "Share QR"))
        }) {
          Text("Save & share QR", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
        }
      }
    }
  }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
  Spacer(Modifier.height(22.dp))
  Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CaptureColors.Navy)
  Spacer(Modifier.height(8.dp))
  content()
}

@Composable
private fun MeetingEmpty(text: String) {
  Text(text, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
}

@Composable
private fun Dot(meeting: Boolean) {
  Box(
    Modifier
      .padding(top = 7.dp)
      .width(7.dp)
      .height(7.dp)
      .clip(CircleShape)
      .background(CaptureColors.Navy.copy(alpha = if (meeting) 0.85f else 0.4f)),
  )
}

/** One action item with owner, priority and deadline, laid out like a mini work card. */
@Composable
private fun WorkItemCard(item: ActionItem) {
  val priority = priorityColor(item.priority)
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(CaptureColors.Paper)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Icon(
        Icons.Filled.RadioButtonUnchecked,
        contentDescription = null,
        tint = CaptureColors.Neutral,
        modifier = Modifier.width(18.dp).height(18.dp).padding(top = 2.dp),
      )
      Spacer(Modifier.width(10.dp))
      Text(item.task, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = CaptureColors.Ink)
    }

    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Tag(label = item.owner.ifBlank { "Unassigned" }, color = CaptureColors.Navy)
      Tag(label = item.confidenceLabel(), color = CaptureColors.Neutral)
      Tag(label = item.priority.capitalize(), color = priority)
      if (!item.deadline.isNullOrBlank()) {
        Tag(label = item.deadline!!, color = CaptureColors.Medium)
      }
    }
  }
}

@Composable
private fun Tag(label: String, color: Color) {
  Box(
    Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.10f))
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = color)
  }
}

private fun ActionItem.confidenceLabel(): String =
  when (ownerConfidence) {
    "named" -> "named"
    "inferred" -> "inferred"
    else -> "unassigned"
  }

private fun String.capitalize(): String = replaceFirstChar { it.uppercase() }