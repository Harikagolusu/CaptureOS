package com.google.ai.edge.gallery.customtasks.captureos

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Writes AI-extracted events into the phone's shared calendar (CalendarContract), so admin and
 * employee see the same entries through whatever account the device already syncs.
 */
object CalendarWriter {

  fun hasPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
      PackageManager.PERMISSION_GRANTED &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

  /** First visible, writable calendar on the device, or null if none. */
  fun writableCalendarId(context: Context): Long? {
    if (!hasPermission(context)) return null
    val projection =
      arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
      )
    val selection =
      "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1"
    val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
    return runCatching {
        context.contentResolver
          .query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)
          ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
      }
      .getOrNull()
  }

  /** Inserts the event into the shared calendar; returns the calendar event id or null. */
  fun write(context: Context, event: CalendarEvent): Long? {
    val calendarId = writableCalendarId(context) ?: return null
    val values =
      ContentValues().apply {
        put(CalendarContract.Events.DTSTART, event.startAt)
        put(CalendarContract.Events.DTEND, event.endAt ?: (event.startAt + 3_600_000L))
        put(CalendarContract.Events.TITLE, event.title)
        put(
          CalendarContract.Events.DESCRIPTION,
          buildString {
            append("Added by CaptureOS")
            if (event.owner.isNotBlank()) append(" · ${event.owner}")
            if (event.priority.isNotBlank()) append(" · ${event.priority} priority")
          },
        )
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
      }
    return runCatching {
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
      }
      .getOrNull()
  }

  fun delete(context: Context, calendarEventId: Long) {
    if (!hasPermission(context)) return
    runCatching {
      val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)
      context.contentResolver.delete(uri, null, null)
    }
  }
}
