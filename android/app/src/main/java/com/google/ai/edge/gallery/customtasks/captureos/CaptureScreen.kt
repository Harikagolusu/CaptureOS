package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * CaptureOS entry point. Routes by the locally stored role:
 *   no profile -> Setup, role "admin" -> record + QR, role "employee" -> scan + personalized tasks.
 */
@Composable
fun CaptureScreen(modelManagerViewModel: ModelManagerViewModel, bottomPadding: Dp) {
  val context = LocalContext.current
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val model = uiState.selectedModel
  var profile by remember { mutableStateOf(CaptureStore.load(context)) }

  LaunchedEffect(Unit) {
    TaskStore.load(context)
    ReminderScheduler.ensureChannel(context)
  }

  Box(Modifier.fillMaxSize()) {
    when {
      !profile.isComplete -> SetupScreen(onDone = { profile = CaptureStore.load(context) })
      profile.role == "admin" -> {
        val ready = uiState.isModelInitialized(model = model)
        if (!ready) {
          LoadingModel()
        } else {
          AdminScreen(profile = profile, model = model, bottomPadding = bottomPadding)
        }
      }
      else -> EmployeeScreen(profile = profile, bottomPadding = bottomPadding)
    }

    if (profile.isComplete) {
      TextButton(
        onClick = {
          CaptureStore.save(context, SetupProfile())
          profile = CaptureStore.load(context)
        },
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
      ) {
        Text(
          "Reset (${profile.name} · ${profile.role})",
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

@Composable
private fun LoadingModel() {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text("Loading Gemma on your phone…", textAlign = TextAlign.Center)
    Text("Runs fully offline — nothing leaves the device.", style = MaterialTheme.typography.bodySmall)
  }
}