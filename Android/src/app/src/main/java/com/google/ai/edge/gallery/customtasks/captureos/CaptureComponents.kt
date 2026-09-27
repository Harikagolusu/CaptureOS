package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** Navy fill, paper text, full-width, 8dp radius — the one primary action per screen. */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(8.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = CaptureColors.Navy,
        contentColor = CaptureColors.Paper,
        disabledContainerColor = CaptureColors.Neutral.copy(alpha = 0.4f),
        disabledContentColor = CaptureColors.Paper,
      ),
    modifier = modifier.fillMaxWidth().height(52.dp),
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
  }
}

/** Small colored dot + word. No filled chip. */
@Composable
private fun DotTag(label: String, color: Color, modifier: Modifier = Modifier) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
  }
}

fun priorityColor(priority: String): Color =
  when (priority.trim().lowercase()) {
    "high" -> CaptureColors.High
    "medium" -> CaptureColors.Medium
    "low" -> CaptureColors.Low
    else -> CaptureColors.Neutral
}

fun priorityLabel(priority: String): String =
  when (priority.trim().lowercase()) {
    "high" -> "High"
    "medium" -> "Medium"
    "low" -> "Low"
    else -> "No priority"
  }

@Composable
fun PriorityTag(priority: String, modifier: Modifier = Modifier) =
  DotTag(priorityLabel(priority), priorityColor(priority), modifier)

fun confidenceColor(confidence: String): Color =
  when (confidence.trim().lowercase()) {
    "named" -> CaptureColors.Low
    "inferred" -> CaptureColors.Medium
    else -> CaptureColors.High
  }

fun confidenceLabel(confidence: String): String =
  when (confidence.trim().lowercase()) {
    "named" -> "Named"
    "inferred" -> "Inferred"
    else -> "Unassigned"
  }

@Composable
fun ConfidenceTag(confidence: String, modifier: Modifier = Modifier) =
  DotTag(confidenceLabel(confidence), confidenceColor(confidence), modifier)

/** Row/card with a colored left-border rail carrying its status. */
@Composable
fun RailRow(
  railColor: Color,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    Box(Modifier.width(3.dp).fillMaxHeight().background(railColor))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).padding(vertical = 12.dp), content = content)
  }
}

@Composable
fun CheckBoxSquare(checked: Boolean) {
  val bg = if (checked) CaptureColors.Navy else Color.Transparent
  val border = if (checked) CaptureColors.Navy else CaptureColors.Neutral
  Box(
    Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(bg).border(1.5.dp, border, RoundedCornerShape(4.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (checked) {
      Icon(Icons.Filled.Check, contentDescription = null, tint = CaptureColors.Paper, modifier = Modifier.size(14.dp))
    }
  }
}

/** Tap toggles strike-through. 200ms ease handled by the strike-through state change. */
@Composable
fun CheckboxRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Spacer(Modifier.height(2.dp))
    CheckBoxSquare(checked)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Box(Modifier.alpha(if (checked) 0.45f else 1f)) { Column(content = content) }
    }
  }
}

/** Large circular navy record button; gently pulses while recording. */
@Composable
fun RecordButton(recording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "pulse")
  val alpha by
    transition.animateFloat(
      initialValue = 1f,
      targetValue = 0.7f,
      animationSpec =
        infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
      label = "alpha",
    )
  Box(
    modifier =
      modifier
        .size(120.dp)
        .alpha(if (recording) alpha else 1f)
        .clip(CircleShape)
        .background(CaptureColors.Navy)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(if (recording) 34.dp else 46.dp)
        .clip(if (recording) RoundedCornerShape(6.dp) else CircleShape)
        .background(CaptureColors.Paper)
    )
  }
}

@Composable
fun RecordTimer(seconds: Int, modifier: Modifier = Modifier) {
  Text(
    "%02d:%02d".format(seconds / 60, seconds % 60),
    style = MaterialTheme.typography.titleMedium,
    color = CaptureColors.Ink,
    modifier = modifier,
  )
}

/** Instructive empty copy — never blank. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = CaptureColors.Neutral,
    modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
  )
}

/** Named processing steps updating in place. */
@Composable
fun LoadingSteps(steps: List<String>, currentIndex: Int, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    steps.forEachIndexed { i, step ->
      val done = i < currentIndex
      val current = i == currentIndex
      Text(
        if (done) "✓  $step" else step,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
        color =
          when {
            done -> CaptureColors.Low
            current -> CaptureColors.Ink
            else -> CaptureColors.Neutral
          },
      )
    }
  }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(1.dp).background(CaptureColors.Hairline))
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
  Text("CaptureOS", style = MaterialTheme.typography.titleLarge, color = CaptureColors.Navy, modifier = modifier)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.headlineMedium, color = CaptureColors.Ink, modifier = modifier)
}

@Composable
fun Meta(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral, modifier = modifier)
}
