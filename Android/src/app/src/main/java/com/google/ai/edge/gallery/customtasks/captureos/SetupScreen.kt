package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(onDone: () -> Unit) {
  val context = LocalContext.current
  var team by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var role by remember { mutableStateOf("employee") }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
  ) {
    Wordmark()
    Spacer(Modifier.height(6.dp))
    Text("One phone. Two outputs.", style = MaterialTheme.typography.bodyMedium, color = CaptureColors.Neutral)
    Spacer(Modifier.height(32.dp))

    FieldLabel("Team code")
    OutlinedTextField(
      value = team,
      onValueChange = { team = it },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      placeholder = { Text("TEAM2026", color = CaptureColors.Neutral) },
      shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
      colors = fieldColors(),
    )
    Spacer(Modifier.height(18.dp))

    FieldLabel("Your name")
    OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      placeholder = { Text("Priya", color = CaptureColors.Neutral) },
      shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
      colors = fieldColors(),
    )
    Spacer(Modifier.height(22.dp))

    FieldLabel("I am a…")
    Column {
      RoleOption("Admin", role == "admin") { role = "admin" }
      RoleOption("Employee", role == "employee") { role = "employee" }
    }

    Spacer(Modifier.height(32.dp))
    PrimaryButton("Continue", onClick = {
      CaptureStore.save(context, SetupProfile(team.trim(), name.trim(), role))
      onDone()
    }, enabled = team.isNotBlank() && name.isNotBlank())
    Spacer(Modifier.height(12.dp))
    Text(
      "Stored on this phone only. No server, no account.",
      style = MaterialTheme.typography.bodySmall,
      color = CaptureColors.Neutral,
    )
  }
}

@Composable
private fun FieldLabel(text: String) {
  Text(text, style = MaterialTheme.typography.labelMedium, color = CaptureColors.Neutral, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun RoleOption(label: String, selected: Boolean, onSelect: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    RadioButton(
      selected = selected,
      onClick = onSelect,
      colors = RadioButtonDefaults.colors(selectedColor = CaptureColors.Navy, unselectedColor = CaptureColors.Neutral),
    )
    Text(label, style = MaterialTheme.typography.bodyLarge, color = CaptureColors.Ink)
  }
}

@Composable
private fun fieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CaptureColors.Navy,
    unfocusedBorderColor = CaptureColors.Hairline,
    cursorColor = CaptureColors.Navy,
    focusedTextColor = CaptureColors.Ink,
    unfocusedTextColor = CaptureColors.Ink,
  )
