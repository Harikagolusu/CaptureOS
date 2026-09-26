// Plain models for backend JSON (see docs/API.md).

String _s(dynamic v) => v == null ? '' : v.toString();
List<T> _list<T>(dynamic v, T Function(Map<String, dynamic>) f) =>
    (v as List? ?? const []).map((e) => f(Map<String, dynamic>.from(e as Map))).toList();

class TeamMember {
  TeamMember.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        name = _s(j['name']),
        aliases = (j['aliases'] as List? ?? const []).map(_s).toList(),
        role = _s(j['role']),
        profile = _s(j['profile']),
        joined = j['joined'] == true;
  final int id;
  final String name;
  final List<String> aliases;
  final String role;
  final String profile;
  final bool joined;
}

class TaskItem {
  TaskItem.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        title = _s(j['title']),
        owner = _s(j['owner']),
        ownerUserId = j['owner_user_id'] as int?,
        due = j['due'] as String?,
        priority = _s(j['priority']),
        status = _s(j['status']),
        meetingId = j['meeting_id'] as int?,
        notionUrl = _s(j['notion_url']);
  final int id;
  final String title;
  final String owner;
  final int? ownerUserId;
  final String? due;
  final String priority;
  String status;
  final int? meetingId;
  final String notionUrl;
  bool get done => status == 'done';
}

class MeetingSummary {
  MeetingSummary.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        title = _s(j['title']),
        status = _s(j['status']),
        error = _s(j['error']),
        summary = _s(j['summary']),
        notionUrl = _s(j['notion_url']),
        kind = _s(j['kind']),
        input = _s(j['input']),
        hostId = j['host_id'] as int?,
        tasks = _list(j['tasks'], TaskItem.fromJson),
        createdAt = DateTime.tryParse(_s(j['created_at']))?.toLocal();
  final int id;
  final String title;
  final String status;
  final String error;
  final String summary;
  final String notionUrl;
  final String kind; // audio | text
  final String input; // what the user typed (text messages)
  final int? hostId;
  final List<TaskItem> tasks;
  final DateTime? createdAt;
  bool get processing => status != 'done' && status != 'error';
}

class Attendee {
  Attendee.fromJson(Map<String, dynamic> j)
      : name = _s(j['name']),
        userId = j['user_id'] as int?,
        speakerLabel = _s(j['speaker_label']),
        update = _s(j['update']);
  final String name;
  final int? userId;
  final String speakerLabel;
  final String update;
}

class Question {
  Question.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        text = _s(j['text']),
        answer = _s(j['answer']),
        status = _s(j['status']);
  final int id;
  final String text;
  final String answer;
  final String status;
}

class Segment {
  Segment.fromJson(Map<String, dynamic> j)
      : speaker = _s(j['speaker']),
        name = _s(j['name']),
        userId = j['user_id'] as int?,
        text = _s(j['text']);
  final String speaker;
  final String name;
  final int? userId;
  final String text;
  String get who => name.isNotEmpty ? name : (speaker.isNotEmpty ? speaker : '—');
}

class MeetingDetail {
  MeetingDetail.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        title = _s(j['title']),
        status = _s(j['status']),
        error = _s(j['error']),
        summary = _s(j['summary']),
        notionUrl = _s(j['notion_url']),
        attendees = _list(j['attendees'], Attendee.fromJson),
        tasks = _list(j['tasks'], TaskItem.fromJson),
        questions = _list(j['questions'], Question.fromJson),
        segments = _list(j['segments'], Segment.fromJson);
  final int id;
  final String title;
  final String status;
  final String error;
  final String summary;
  final String notionUrl;
  final List<Attendee> attendees;
  final List<TaskItem> tasks;
  final List<Question> questions;
  final List<Segment> segments;
  bool get processing => status != 'done' && status != 'error';
}

class InboxItem {
  InboxItem.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        kind = _s(j['kind']),
        source = _s(j['source']),
        status = _s(j['status']),
        album = _s(j['album']),
        category = _s(j['category']),
        people = j['people'] == true,
        actionable = j['actionable'] == true,
        title = _s(j['title']),
        text = _s(j['text']),
        summary = _s(j['summary']),
        taskIds = (j['task_ids'] as List? ?? const []).cast<int>(),
        meetingId = j['meeting_id'] as int?,
        fileUrl = _s(j['file_url']),
        error = _s(j['error']);
  final int id;
  final String kind;
  final String source;
  final String status;
  final String album;
  final String category;
  final bool people;
  final bool actionable;
  final String title;
  final String text;
  final String summary;
  final List<int> taskIds;
  final int? meetingId;
  final String fileUrl;
  final String error;
  bool get isPhoto => kind == 'photo';
}

class Album {
  Album.fromJson(Map<String, dynamic> j)
      : name = _s(j['album']),
        count = j['count'] as int? ?? 0,
        coverUrl = _s(j['cover_url']);
  final String name;
  final int count;
  final String coverUrl;
}

class FieldObservation {
  FieldObservation.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        status = _s(j['status']),
        error = _s(j['error']),
        title = _s(j['title']),
        observation = _s(j['observation']),
        category = _s(j['category']),
        severity = _s(j['severity']),
        action = _s(j['action']),
        owner = _s(j['owner']),
        voiceText = _s(j['voice_text']),
        note = _s(j['note']),
        photoUrl = _s(j['photo_url']);
  final int id;
  final String status;
  final String error;
  final String title;
  final String observation;
  final String category;
  final String severity;
  final String action;
  final String owner;
  final String voiceText;
  final String note;
  final String photoUrl;
  bool get processing => status == 'processing';
}

class FieldVisit {
  FieldVisit.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        site = _s(j['site']),
        reportStatus = _s(j['report_status']),
        reportError = _s(j['report_error']),
        summary = _s(j['summary']),
        keyIssues = (j['key_issues'] as List? ?? const []).map(_s).toList(),
        recommendations = (j['recommendations'] as List? ?? const []).map(_s).toList(),
        reportUrl = _s(j['report_url']),
        notionUrl = _s(j['notion_url']),
        createdAt = DateTime.tryParse(_s(j['created_at']))?.toLocal(),
        observations = _list(j['observations'], FieldObservation.fromJson);
  final int id;
  final String site;
  final String reportStatus;
  final String reportError;
  final String summary;
  final List<String> keyIssues;
  final List<String> recommendations;
  final String reportUrl;
  final String notionUrl;
  final DateTime? createdAt;
  final List<FieldObservation> observations;
}
