import 'package:flutter_test/flutter_test.dart';
import 'package:phone/models/observation.dart';

void main() {
  group('ObservationDraft', () {
    test('creation with required fields', () {
      final draft = ObservationDraft(
        id: 'test-123',
        capturedAt: DateTime(2026, 9, 26, 10, 30),
        title: 'Test',
      );
      expect(draft.id, 'test-123');
      expect(draft.status, ObservationStatus.draft);
      expect(draft.hasPhoto, isFalse);
      expect(draft.hasAudio, isFalse);
      expect(draft.isApproved, isFalse);
    });

    test('hasPhoto and hasAudio', () {
      final d = ObservationDraft(
        id: '1',
        capturedAt: DateTime.now(),
        photoPath: '/tmp/a.jpg',
        audioPath: '/tmp/b.wav',
      );
      expect(d.hasPhoto, isTrue);
      expect(d.hasAudio, isTrue);
    });

    test('copyWith and approved', () {
      final d = ObservationDraft(id: '1', capturedAt: DateTime.now());
      final approved = d.approved();
      expect(approved.isApproved, isTrue);
      expect(approved.status, ObservationStatus.approved);
      expect(d.isApproved, isFalse); // original unchanged
    });

    test('copyWith preserves fields', () {
      final d = ObservationDraft(id: '1', capturedAt: DateTime.now(), title: 'A');
      final c = d.copyWith(title: 'B', notes: 'note');
      expect(c.title, 'B');
      expect(c.notes, 'note');
      expect(c.id, '1');
    });

    test('toJson contains expected keys', () {
      final d = ObservationDraft(id: '1', capturedAt: DateTime.utc(2026, 1, 1), photoPath: 'p.jpg');
      final j = d.toJson();
      expect(j['id'], '1');
      expect(j['photoPath'], 'p.jpg');
      expect(j['status'], 'draft');
    });
  });
}
