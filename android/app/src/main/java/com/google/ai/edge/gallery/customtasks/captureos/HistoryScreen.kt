package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val historyDateFormat = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

fun eventColor(event: CalendarEvent): androidx.compose.ui.graphics.Color =
  when (event.kind) {
    "meeting" -> CaptureColors.Navy
    "deadline" -> CaptureColors.High
    else -> priorityColor(event.priority)
  }

/** Admin History: upcoming deadlines and meetings, archived meetings with their QR, activity log. */
@Composable
fun HistoryScreen(bottomPadding: Dp) {
  val context = LocalContext.current
  CaptureDb.load(context)
  var expandedMeeting by remember { mutableStateOf<String?>(null) }

  val upcoming = CaptureDb.upcomingEvents()
  val past = CaptureDb.pastEvents()
  val meetings = CaptureDb.state.meetings
  val activity = CaptureDb.state.activity

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(horizontal = 20.dp),
  ) {
    Spacer(Modifier.height(24.dp))
    Text("History", style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink)

    // ---- calendar ----
    Spacer(Modifier.height(20.dp))
    Text("Upcoming", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(4.dp))
    if (upcoming.isEmpty()) {
      EmptyState("Nothing scheduled yet — dictate a deadline and it lands here.")
    } else {
      upcoming.forEach { event ->
        RailRow(railColor = eventColor(event)) {
          Text(event.title, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
          Spacer(Modifier.height(2.dp))
          Meta("${event.kind} · ${historyDateFormat.format(Date(event.startAt))}${if (event.owner.isNotBlank()) " · ${event.owner}" else ""}")
          Spacer(Modifier.height(6.dp))
          TextButton(onClick = {
            CaptureDb.updateEvent(context, event.copy(status = "done"))
            ReminderChain.cancel(context, event.id)
          }) {
            Text("Mark done", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Navy)
          }
        }
        Hairline()
      }
    }

    if (past.isNotEmpty()) {
      Spacer(Modifier.height(16.dp))
      Text("Past", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
      past.take(5).forEach { event ->
        Spacer(Modifier.height(6.dp))
        Meta("${event.title} · ${historyDateFormat.format(Date(event.startAt))}")
      }
    }

    // ---- meetings ----
    Spacer(Modifier.height(24.dp))
    Text("Meetings", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(4.dp))
    if (meetings.isEmpty()) {
      EmptyState("No meetings archived yet — record one from the Meetings tab.")
    } else {
      meetings.forEach { meeting ->
        MeetingRow(
          meeting = meeting,
          expanded = expandedMeeting == meeting.id,
          onToggle = { expandedMeeting = if (expandedMeeting == meeting.id) null else meeting.id },
        )
        Hairline()
      }
    }

    // ---- activity ----
    Spacer(Modifier.height(24.dp))
    Text("Activity", style = MaterialTheme.typography.titleMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(4.dp))
    if (activity.isEmpty()) {
      EmptyState("No activity yet.")
    } else {
      activity.take(25).forEach { line ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
          Meta(SimpleDateFormat("d MMM h:mm a", Locale.getDefault()).format(Date(line.at)))
          Spacer(Modifier.width(10.dp))
          Text(line.detail, style = MaterialTheme.typography.bodySmall, color = CaptureColors.Ink)
        }
      }
    }
    Spacer(Modifier.height(28.dp))
  }
}

@Composable
private fun MeetingRow(meeting: MeetingArchive, expanded: Boolean, onToggle: () -> Unit) {
  val context = LocalContext.current
  Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
    RailRow(railColor = CaptureColors.Navy) {
      Text(meeting.meetingTitle, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
      Spacer(Modifier.height(2.dp))
      Meta(
        buildString {
          append(historyDateFormat.format(Date(meeting.createdAt)))
          if (meeting.hostName.isNotBlank()) append(" · host ${meeting.hostName}")
          append(" · team ${meeting.teamCode}")
          append(if (meeting.status == "recorded") " · recorded" else " · not recorded yet")
        }
      )
      if (expanded) {
        Spacer(Modifier.height(10.dp))
        if (meeting.summary.isNotBlank()) {
          Text("Summary", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
          Text(meeting.summary, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
        }
        if (meeting.decisions.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text("Decisions", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
          meeting.decisions.forEach { Text("— $it", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink) }
        }
        if (meeting.actionItems.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text("Action items", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
          meeting.actionItems.forEach { a ->
            Text("• ${a.task} — ${a.owner} (${a.confidenceText()})", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
          }
        }
        if (meeting.transcript.isNotBlank()) {
          Spacer(Modifier.height(8.dp))
          Text("Transcript", style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral)
          Text(meeting.transcript, style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Ink)
        }

        val qr: Bitmap? =
          remember(meeting.id, meeting.status) {
            if (meeting.status != "recorded") {
              null
            } else {
              runCatching {
                  QrCodec.meetingJsonToQr(
                    MeetingRecord(
                      teamCode = meeting.teamCode,
                      meetingTitle = meeting.meetingTitle,
                      summary = meeting.summary,
                      decisions = meeting.decisions,
                      actionItems = meeting.actionItems,
                    )
                  )
                }
                .getOrNull()
            }
          }
        if (qr != null) {
          Spacer(Modifier.height(12.dp))
          Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = "Meeting QR",
            modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(8.dp)),
          )
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
  }
}

/** Employee "Meetings" tab: the meetings they have scanned into. */
@Composable
fun MeetingsListScreen(bottomPadding: Dp, onScan: () -> Unit) {
  val context = LocalContext.current
  CaptureDb.load(context)
  var expanded by remember { mutableStateOf<String?>(null) }
  val meetings = CaptureDb.state.meetings

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding).padding(horizontal = 20.dp),
  ) {
    Spacer(Modifier.height(24.dp))
    Text("Meetings", style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink)
    Spacer(Modifier.height(4.dp))
    Text("Meetings you've scanned into.", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
    Spacer(Modifier.height(14.dp))
    PrimaryButton("Scan a meeting QR", onClick = onScan)
    Spacer(Modifier.height(14.dp))
    if (meetings.isEmpty()) {
      EmptyState("No meetings yet — scan a QR from the Scan tab.")
    } else {
      meetings.forEach { meeting ->
        MeetingRow(
          meeting = meeting,
          expanded = expanded == meeting.id,
          onToggle = { expanded = if (expanded == meeting.id) null else meeting.id },
        )
        Hairline()
      }
    }
    Spacer(Modifier.height(28.dp))
  }
}

private fun ActionItem.confidenceText(): String =
  when (ownerConfidence) {
    "named" -> "named"
    "inferred" -> "inferred"
    else -> "unassigned"
  }
