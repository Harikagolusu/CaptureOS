package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(32.dp))
    Text("CaptureOS setup", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
      "Stored only on this phone. No server, no account.",
      style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
      value = team,
      onValueChange = { team = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Team code") },
      singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Your name") },
      singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Text("I am the…", style = MaterialTheme.typography.titleMedium)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("admin" to "Admin — records the meeting", "employee" to "Employee — scans the QR").forEach { (v, label) ->
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = role == v, onClick = { role = v })
          Text(label)
        }
      }
    }
    Spacer(Modifier.height(24.dp))
    Button(
      onClick = {
        CaptureStore.save(context, SetupProfile(team.trim(), name.trim(), role))
        onDone()
      },
      enabled = team.isNotBlank() && name.isNotBlank(),
      modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
  }
}