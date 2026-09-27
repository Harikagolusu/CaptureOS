package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope

const val CAPTURE_OS_TASK_ID = "captureos"

/** CaptureOS: record a meeting or type a note; on-device Gemma turns it into tasks with reminders. */
class CaptureTask : CustomTask {
  override val task =
    Task(
      id = CAPTURE_OS_TASK_ID,
      label = "CaptureOS",
      category = Category.LLM,
      icon = Icons.Outlined.Mic,
      description =
        "Capture, Meetings, Diary. Record a meeting (Telugu, Hindi, English) and Gemma turns it into " +
          "decisions and action items shown as a QR code; teammates scan it and each sees only their " +
          "own tasks. Or record quick diary notes that become key points and a to-do list — fully offline.",
      shortDescription = "Meetings → tasks → reminders, offline",
      models = mutableListOf(),
      // Picked from the model catalogue; they take audio input.
      modelNames = listOf("Gemma-4-E2B-it", "Gemma-4-E4B-it", "Gemma-3n-E2B-it", "Gemma-3n-E4B-it"),
      newFeature = true,
    )

  override val keepModelAlive: Boolean = true

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      taskId = task.id,
      supportImage = true,
      supportAudio = true,
      onDone = onDone,
    )
  }

  override fun cleanUpModelFn(context: Context, coroutineScope: CoroutineScope, model: Model, onDone: () -> Unit) {
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val d = data as CustomTaskData
    CaptureScreen(modelManagerViewModel = d.modelManagerViewModel, bottomPadding = d.bottomPadding)
  }
}

@Module
@InstallIn(SingletonComponent::class)
internal object CaptureTaskModule {
  @Provides @IntoSet fun provideTask(): CustomTask = CaptureTask()
}
