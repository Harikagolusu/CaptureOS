import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/models/observation.dart';
import 'package:phone/screens/evidence_screen.dart';

void main() {
  testWidgets('EvidenceScreen shows evidence list', (tester) async {
    final draft = ObservationDraft(
      id: '1',
      capturedAt: DateTime.now(),
      photoPath: '/mock/photo.jpg',
      audioPath: '/mock/voice.wav',
      location: 'Site A',
    );
    await tester.pumpWidget(MaterialApp(home: EvidenceScreen(draft: draft)));
    expect(find.text('Evidence'), findsWidgets);
    expect(find.textContaining('photo'), findsWidgets);
    expect(find.text('Add Evidence'), findsOneWidget);
  });

  testWidgets('EvidenceScreen empty state', (tester) async {
    final draft = ObservationDraft(id: '1', capturedAt: DateTime.now());
    await tester.pumpWidget(MaterialApp(home: EvidenceScreen(draft: draft)));
    expect(find.text('No evidence yet'), findsOneWidget);
  });
}
