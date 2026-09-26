class Evidence {
  const Evidence({
    required this.id,
    required this.observationId,
    required this.type,
    required this.path,
    required this.timestamp,
    this.location,
  });

  final String id;
  final String observationId;
  final String type; // 'photo' | 'voice'
  final String path;
  final DateTime timestamp;
  final String? location;

  String get displayName => path.split('/').last;
}
