import 'package:flutter_test/flutter_test.dart';
import 'package:phone/models/observation.dart';
import 'package:phone/services/mock_observation_service.dart';

void main() {
  test('MockObservationService returns mock observations', () async {
    final svc = MockObservationService();
    final list = await svc.getObservations();
    expect(list.length, 3);
    expect(list.first.title, isNotNull);
  });

  test('Mock service create and approve', () async {
    final svc = MockObservationService(seed: []);
    final draft = ObservationDraft(id: 'test', capturedAt: DateTime.now(), title: 'Test', status: ObservationStatus.draft);
    await svc.createObservation(draft);
    var list = await svc.getObservations();
    expect(list.length, 1);
    await svc.approveObservation('test');
    list = await svc.getObservations();
    expect(list.first.isApproved, isTrue);
  });

  test('Mock service queue filters', () async {
    final svc = MockObservationService();
    final queue = await svc.getQueue();
    expect(queue.isNotEmpty, isTrue);
  });
}
