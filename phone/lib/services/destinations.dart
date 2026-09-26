import 'package:device_calendar/device_calendar.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:timezone/data/latest.dart' as tzdata;

import '../core/api_client.dart';
import '../core/share.dart';
import '../models/api_models.dart';

/// Where results go on the phone: calendar reminders and the Notes app.
class Destinations {
  Destinations._();

  static final _calendar = DeviceCalendarPlugin();
  static bool _tzReady = false;

  /// Adds each task that has a due date as a 9:00 event with a 30-minute reminder in the phone's
  /// default writable calendar. Returns how many were added (0 if permission was refused).
  static Future<int> addToCalendar(List<TaskItem> tasks) async {
    final dated = tasks.where((t) => t.due != null && DateTime.tryParse(t.due!) != null).toList();
    if (dated.isEmpty) return 0;
    if (!_tzReady) {
      tzdata.initializeTimeZones();
      setLocalLocation(getLocation('Asia/Kolkata'));
      _tzReady = true;
    }
    var granted = (await _calendar.hasPermissions()).data ?? false;
    if (!granted) granted = (await _calendar.requestPermissions()).data ?? false;
    if (!granted) return 0;
    final calendars = (await _calendar.retrieveCalendars()).data ?? [];
    final writable = calendars.where((c) => c.isReadOnly != true).toList();
    if (writable.isEmpty) return 0;
    final cal = writable.firstWhere((c) => c.isDefault == true, orElse: () => writable.first);
    var added = 0;
    for (final t in dated) {
      final day = DateTime.parse(t.due!);
      final start = TZDateTime(local, day.year, day.month, day.day, 9);
      final event = Event(
        cal.id,
        title: t.title,
        description: 'Owner: ${t.owner} · priority ${t.priority}\nfrom CaptureOS',
        start: start,
        end: start.add(const Duration(minutes: 30)),
        reminders: [Reminder(minutes: 30)],
      );
      final r = await _calendar.createOrUpdateEvent(event);
      if (r?.isSuccess == true) added++;
    }
    return added;
  }

  /// Puts every one of my open tasks that has a due date into the calendar, once each
  /// (from meetings, notes, photos alike). Returns how many were newly added.
  static Future<int> syncMyTasksToCalendar() async {
    final prefs = await SharedPreferences.getInstance();
    final synced = (prefs.getStringList('calendar_synced') ?? []).toSet();
    final rows = await ApiClient.instance.get('/me/tasks?status=todo') as List;
    final fresh = rows
        .map((e) => TaskItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .where((t) => t.due != null && !synced.contains('${t.id}'))
        .toList();
    if (fresh.isEmpty) return 0;
    final added = await addToCalendar(fresh);
    if (added > 0) {
      synced.addAll(fresh.map((t) => '${t.id}'));
      await prefs.setStringList('calendar_synced', synced.toList());
    }
    return added;
  }

  /// Opens the share sheet pre-filled with the meeting note (user taps Notes / vivo Notes).
  static Future<void> shareToNotes(String title, String summary, List<TaskItem> tasks) {
    final b = StringBuffer('$title\n\n$summary\n');
    if (tasks.isNotEmpty) {
      b.writeln('\nTasks:');
      for (final t in tasks) {
        b.writeln('☐ ${t.title} → ${t.owner}${t.due != null ? ' (due ${t.due})' : ''}');
      }
    }
    b.write('\n— CaptureOS');
    return shareText(b.toString(), subject: title);
  }
}
