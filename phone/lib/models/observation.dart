import 'evidence.dart';

enum ObservationStatus { draft, needsReview, approved, queued, exported }
enum ObservationCategory { safety, equipment, environment, maintenance, other }
enum ObservationPriority { low, medium, high, critical }

extension ObservationCategoryLabel on ObservationCategory {
  String get label => switch (this) {
        ObservationCategory.safety => 'Safety',
        ObservationCategory.equipment => 'Equipment',
        ObservationCategory.environment => 'Environment',
        ObservationCategory.maintenance => 'Maintenance',
        ObservationCategory.other => 'Other',
      };
}

extension ObservationPriorityLabel on ObservationPriority {
  String get label => switch (this) {
        ObservationPriority.low => 'Low',
        ObservationPriority.medium => 'Medium',
        ObservationPriority.high => 'High',
        ObservationPriority.critical => 'Critical',
      };
}

extension ObservationStatusLabel on ObservationStatus {
  String get label => switch (this) {
        ObservationStatus.draft => 'Draft',
        ObservationStatus.needsReview => 'Needs Review',
        ObservationStatus.approved => 'Approved',
        ObservationStatus.queued => 'Pending',
        ObservationStatus.exported => 'Exported',
      };
}

class ObservationDraft {
  const ObservationDraft({
    required this.id,
    required this.capturedAt,
    this.photoPath,
    this.audioPath,
    this.title,
    this.description,
    this.location,
    this.category = ObservationCategory.other,
    this.priority = ObservationPriority.medium,
    this.notes,
    this.status = ObservationStatus.draft,
    this.metadata = const {},
    this.evidence = const [],
  });

  final String id;
  final DateTime capturedAt;
  final String? photoPath;
  final String? audioPath;
  final String? title;
  final String? description;
  final String? location;
  final ObservationCategory category;
  final ObservationPriority priority;
  final String? notes;
  final ObservationStatus status;
  final Map<String, String> metadata;
  final List<Evidence> evidence;

  bool get hasPhoto => photoPath != null && photoPath!.isNotEmpty;
  bool get hasAudio => audioPath != null && audioPath!.isNotEmpty;
  bool get isApproved => status == ObservationStatus.approved;
  int get evidenceCount => evidence.length + (hasPhoto ? 1 : 0) + (hasAudio ? 1 : 0);

  ObservationDraft copyWith({
    String? photoPath,
    String? audioPath,
    String? title,
    String? description,
    String? location,
    ObservationCategory? category,
    ObservationPriority? priority,
    String? notes,
    ObservationStatus? status,
    Map<String, String>? metadata,
    List<Evidence>? evidence,
  }) {
    return ObservationDraft(
      id: id,
      capturedAt: capturedAt,
      photoPath: photoPath ?? this.photoPath,
      audioPath: audioPath ?? this.audioPath,
      title: title ?? this.title,
      description: description ?? this.description,
      location: location ?? this.location,
      category: category ?? this.category,
      priority: priority ?? this.priority,
      notes: notes ?? this.notes,
      status: status ?? this.status,
      metadata: metadata ?? this.metadata,
      evidence: evidence ?? this.evidence,
    );
  }

  ObservationDraft approved() => copyWith(status: ObservationStatus.approved);
  ObservationDraft needsReview() => copyWith(status: ObservationStatus.needsReview);

  Map<String, dynamic> toJson() => {
        'id': id,
        'capturedAt': capturedAt.toIso8601String(),
        'photoPath': photoPath,
        'audioPath': audioPath,
        'title': title,
        'description': description,
        'location': location,
        'category': category.name,
        'priority': priority.name,
        'status': status.name,
      };
}
