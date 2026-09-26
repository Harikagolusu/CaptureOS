package com.google.ai.edge.gallery.customtasks.captureos

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/** Functions the on-device Gemma model calls while reading a transcript. */
class CaptureTools(val onTask: (CapturedTask) -> Unit) : ToolSet {

  @Tool(description = "Save one action item that someone agreed to do.")
  fun createTask(
    @ToolParam(description = "Short imperative task title in English, e.g. 'Send the revised quotation'.")
    title: String,
    @ToolParam(description = "Name of the person who will do it, or an empty string if nobody took it.")
    owner: String,
    @ToolParam(description = "Due date as YYYY-MM-DD resolved from words like 'Friday', or an empty string.")
    dueDate: String,
    @ToolParam(description = "Due time as HH:MM in 24-hour format if a time was said, or an empty string.")
    dueTime: String,
    @ToolParam(description = "One of: high, medium, low.") priority: String,
  ): Map<String, String> {
    onTask(
      CapturedTask(
        title = title.trim(),
        owner = owner.trim(),
        dueDate = dueDate.trim(),
        dueTime = dueTime.trim(),
        priority = priority.trim().lowercase().ifBlank { "medium" },
      )
    )
    return mapOf("result" to "saved")
  }
}
