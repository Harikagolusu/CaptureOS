package com.google.ai.edge.gallery.customtasks.captureos

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class MeetingRecord(
  @SerializedName("team_code") val teamCode: String,
  @SerializedName("meeting_title") val meetingTitle: String,
  val summary: String,
  val decisions: List<String>,
  @SerializedName("action_items") val actionItems: List<ActionItem>,
)

data class ActionItem(
  val task: String,
  val owner: String,
  @SerializedName("owner_confidence") val ownerConfidence: String, // "named" | "inferred" | "unassigned"
  val deadline: String?, // nullable, free-form
  val priority: String, // "high" | "medium" | "low"
)

val captureOsJsonSchemaInstruction: String
  get() = """
You are a meeting assistant that extracts structured data from transcripts.

Return ONLY a valid JSON object with this exact shape (no markdown, no explanation):
```json
{
  "team_code": "<insert team code here>",
  "meeting_title": "...",
  "summary": "...",
  "decisions": ["..."],
  "action_items": [
    {
      "task": "...",
      "owner": "...",
      "owner_confidence": "named|inferred|unassigned",
      "deadline": "..." or null,
      "priority": "high|medium|low"
    }
  ]
}
```

owner_confidence rules:
- "named": a real name was spoken and clearly tied to this task.
- "inferred": someone said "I'll do it" or similar but no name was said → label owner as "Speaker 1", "Speaker 2", etc.
- "unassigned": nobody claimed the task → owner is "Unassigned".

Extract every action item. Skip small talk and ideas that were dropped.
""".trimIndent()

val meetingJsonGson = Gson()