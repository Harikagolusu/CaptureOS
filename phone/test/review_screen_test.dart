import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/models/observation.dart';
import 'package:phone/screens/review_screen.dart';

void main() {
  testWidgets('ReviewScreen shows photo placeholder when no photo', (tester) async {
    final draft = ObservationDraft(id: '1', capturedAt: DateTime.now());
    await tester.pumpWidget(MaterialApp(home: ReviewScreen(draft: draft)));
    expect(find.text('No photo captured'), findsOneWidget);
    expect(find.text('Approve Observation'), findsOneWidget);
  });

  testWidgets('ReviewScreen shows evidence chips', (tester) async {
    final draft = ObservationDraft(
      id: '1',
      capturedAt: DateTime.now(),
      photoPath: '/tmp/a.jpg',
      audioPath: '/tmp/b.wav',
    );
    await tester.pumpWidget(MaterialApp(home: ReviewScreen(draft: draft)));
    expect(find.text('Photo attached'), findsOneWidget);
    expect(find.text('Voice note'), findsOneWidget);
  });

  testWidgets('ReviewScreen approve changes state', (tester) async {
    final draft = ObservationDraft(id: '1', capturedAt: DateTime.now());
    await tester.pumpWidget(MaterialApp(home: ReviewScreen(draft: draft)));
    expect(find.text('Approve Observation'), findsOneWidget);
    await tester.ensureVisible(find.text('Approve Observation'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Approve Observation'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.textContaining('Approved'), findsWidgets);
  });

  testWidgets('ReviewScreen requires human approval text', (tester) async {
    final draft = ObservationDraft(id: '1', capturedAt: DateTime.now());
    await tester.pumpWidget(MaterialApp(home: ReviewScreen(draft: draft)));
    expect(find.text('Human approval is mandatory before export.'), findsOneWidget);
  });
}
