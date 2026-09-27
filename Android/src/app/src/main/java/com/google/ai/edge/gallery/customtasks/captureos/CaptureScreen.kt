package com.google.ai.edge.gallery.customtasks.captureos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

private data class NavTab(val key: String, val label: String, val icon: ImageVector)

private val TABS =
  listOf(
    NavTab("capture", "Capture", Icons.Filled.Mic),
    NavTab("meetings", "Meetings", Icons.Filled.Groups),
    NavTab("tasks", "Tasks", Icons.Filled.EditNote),
    NavTab("history", "History", Icons.Filled.History),
  )

/**
 * CaptureOS root. No roles, no setup: the app opens straight into the tabs.
 * Anyone can record a meeting; anyone can scan a meeting QR and check in by name.
 */
@Composable
fun CaptureScreen(modelManagerViewModel: ModelManagerViewModel, bottomPadding: Dp) {
  val context = LocalContext.current
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val model: Model = uiState.selectedModel
  var tab by remember { mutableIntStateOf(0) }
  var showScan by remember { mutableStateOf(false) }
  val profile = remember { CaptureStore.load(context) }

  LaunchedEffect(Unit) {
    TaskStore.load(context)
    ReminderScheduler.ensureChannel(context)
    ReminderChain.ensureChannel(context)
    CaptureDb.load(context)
  }

  CaptureTheme {
    Box(Modifier.fillMaxSize().background(CaptureColors.Paper)) {
      if (showScan) {
        MeetingScanScreen(onClose = { showScan = false })
      } else {
        val safeIndex = tab.coerceIn(0, TABS.lastIndex)
        val initStatus by model.initStatusFlow.collectAsState()
        val modelReady = initStatus is Model.InitializationStatus.Initialized

        Scaffold(
          modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
          containerColor = CaptureColors.Paper,
          bottomBar = {
            NavigationBar(containerColor = CaptureColors.Paper) {
              TABS.forEachIndexed { i, t ->
                NavigationBarItem(
                  selected = safeIndex == i,
                  onClick = { tab = i },
                  icon = { Icon(t.icon, contentDescription = t.label) },
                  label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = CaptureColors.Navy,
                      selectedTextColor = CaptureColors.Navy,
                      indicatorColor = CaptureColors.Navy.copy(alpha = 0.10f),
                      unselectedIconColor = CaptureColors.Neutral,
                      unselectedTextColor = CaptureColors.Neutral,
                    ),
                )
              }
            }
          },
        ) { inner ->
          Box(Modifier.fillMaxSize().padding(inner)) {
            when (TABS[safeIndex].key) {
              "capture" -> if (modelReady) DictoCaptureScreen(model, 0.dp) else LoadingModel()
              "meetings" -> if (modelReady) AdminScreen(profile, model, 0.dp, onScan = { showScan = true }) else LoadingModel()
              "tasks" -> TasksScreen(0.dp)
              else -> HistoryScreen(0.dp)
            }
          }
        }
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
    CircularProgressIndicator(color = CaptureColors.Navy)
    Spacer(Modifier.height(16.dp))
    Text("Loading Gemma on your phone…", textAlign = TextAlign.Center, color = CaptureColors.Ink)
    Text("Runs fully offline — nothing leaves the device.", style = MaterialTheme.typography.bodySmall, color = CaptureColors.Neutral)
  }
}
