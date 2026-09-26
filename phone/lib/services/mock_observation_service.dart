import '../models/evidence.dart';
import '../models/observation.dart';
import 'observation_service.dart';

class MockObservationService implements ObservationService {
  final List<ObservationDraft> _store;

  MockObservationService({List<ObservationDraft>? seed}) : _store = seed ?? _mockObservations();

  static List<ObservationDraft> _mockObservations() {
    final now = DateTime.now();
    return [
      ObservationDraft(
        id: '001',
        capturedAt: now.subtract(const Duration(hours: 2)),
        title: 'Scaffold check - Bay 3',
        description: 'Scaffold joints need re-torque, visible rust on cross-brace.',
        location: 'Site A - Bay 3',
        category: ObservationCategory.safety,
        priority: ObservationPriority.high,
        status: ObservationStatus.needsReview,
        photoPath: '/mock/photo_001.jpg',
        audioPath: '/mock/voice_001.wav',
        evidence: [
          Evidence(id: 'e1', observationId: '001', type: 'photo', path: '/mock/photo_001.jpg', timestamp: DateTime(2026, 9, 26)),
        ],
      ),
      ObservationDraft(
        id: '002',
        capturedAt: now.subtract(const Duration(hours: 5)),
        title: 'Generator oil leak',
        description: 'Minor leak near filter housing, monitor level.',
        location: 'Site A - Generator room',
        category: ObservationCategory.equipment,
        priority: ObservationPriority.medium,
        status: ObservationStatus.draft,
        photoPath: '/mock/photo_002.jpg',
        evidence: const [],
      ),
      ObservationDraft(
        id: '003',
        capturedAt: now.subtract(const Duration(days: 1)),
        title: 'Drainage block - North trench',
        description: 'Water pooling after rain, trench obstructed.',
        location: 'North trench',
        category: ObservationCategory.environment,
        priority: ObservationPriority.critical,
        status: ObservationStatus.approved,
        photoPath: '/mock/photo_003.jpg',
        audioPath: '/mock/voice_003.wav',
        evidence: [
          Evidence(id: 'e3', observationId: '003', type: 'photo', path: '/mock/photo_003.jpg', timestamp: DateTime(2026, 9, 25)),
          Evidence(id: 'e4', observationId: '003', type: 'voice', path: '/mock/voice_003.wav', timestamp: DateTime(2026, 9, 25)),
        ],
      ),
    ];
  }

  @override
  Future<List<ObservationDraft>> getObservations() async {
    await Future<void>.delayed(const Duration(milliseconds: 250));
    return List.unmodifiable(_store);
  }

  @override
  Future<ObservationDraft> createObservation(ObservationDraft observation) async {
    await Future<void>.delayed(const Duration(milliseconds: 200));
    _store.insert(0, observation);
    return observation;
  }

  @override
  Future<ObservationDraft> updateObservation(ObservationDraft observation) async {
    final idx = _store.indexWhere((e) => e.id == observation.id);
    if (idx != -1) _store[idx] = observation;
    return observation;
  }

  @override
  Future<void> approveObservation(String id) async {
    final idx = _store.indexWhere((e) => e.id == id);
    if (idx != -1) _store[idx] = _store[idx].approved();
  }

  @override
  Future<List<ObservationDraft>> getQueue() async {
    await Future<void>.delayed(const Duration(milliseconds: 150));
    return _store.where((o) => o.status == ObservationStatus.queued || o.status == ObservationStatus.needsReview || o.status == ObservationStatus.draft).toList();
  }

  @override
  Future<ObservationDraft?> getById(String id) async {
    try {
      return _store.firstWhere((e) => e.id == id);
    } catch (_) {
      return null;
    }
  }
}
