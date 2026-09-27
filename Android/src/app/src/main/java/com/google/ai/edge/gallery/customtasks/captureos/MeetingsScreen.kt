package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val meetingListDateFormat = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

/** Meetings tab: every archived meeting, newest first. Tap one to open its full page. */
@Composable
fun MeetingsScreen(
  model: Model,
  profile: SetupProfile,
  bottomPadding: Dp,
  onScan: () -> Unit,
  onOpenMeeting: (MeetingArchive) -> Unit,
) {
  val context = LocalContext.current
  CaptureDb.load(context)
  val meetings = CaptureDb.state.meetings

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(horizontal = 20.dp)) {
    Spacer(Modifier.height(24.dp))
    Text("Meetings", style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(4.dp))
    Text("Every meeting on this device. Tap one to open the full write-up.", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
    Spacer(Modifier.height(14.dp))

    PrimaryButton("Scan a meeting QR", onClick = onScan)
    Spacer(Modifier.height(14.dp))

    if (meetings.isEmpty()) {
      MeetingEmptyState("No meetings yet — swipe up on Capture to record your first one.")
    } else {
      meetings.forEach { meeting ->
        MeetingListCard(meeting = meeting, onClick = { onOpenMeeting(meeting) })
        Hairline()
      }
    }
    Spacer(Modifier.height(28.dp))
  }
}

@Composable
private fun MeetingListCard(meeting: MeetingArchive, onClick: () -> Unit) {
  Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    RailRow(railColor = CaptureColors.Navy) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Text(
            if (meeting.meetingTitle.isBlank()) "Meeting" else meeting.meetingTitle,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = CaptureColors.Ink,
          )
          Spacer(Modifier.height(2.dp))
          Text(
            buildString {
              append(meetingListDateFormat.format(Date(meeting.createdAt)))
              if (meeting.hostName.isNotBlank()) append("  ·  ${meeting.hostName}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = CaptureColors.Neutral,
          )
          if (meeting.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
              meeting.summary.take(120) + if (meeting.summary.length > 120) "…" else "",
              style = MaterialTheme.typography.bodyMedium,
              color = CaptureColors.Ink,
            )
          }
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            meeting.actionItems.size.takeIf { it > 0 }?.let { "$it action item${if (it == 1) "" else "s"}" } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = CaptureColors.Neutral,
          )
        }
      }
    }
  }
}

@Composable
private fun MeetingEmptyState(text: String) {
  Text(text, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
}