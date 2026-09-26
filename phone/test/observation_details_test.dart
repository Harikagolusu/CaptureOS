import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/models/observation.dart';
import 'package:phone/screens/observation_details_screen.dart';

void main() {
  testWidgets('ObservationDetails form shows required fields', (tester) async {
    final draft = ObservationDraft(id: '1', capturedAt: DateTime.now());
    await tester.pumpWidget(MaterialApp(home: ObservationDetailsScreen(draft: draft)));
    expect(find.text('Observation Title *'), findsOneWidget);
    expect(find.text('Description *'), findsOneWidget);
    expect(find.text('Location *'), findsOneWidget);
    expect(find.text('Category *'), findsOneWidget);
    expect(find.text('Priority *'), findsOneWidget);
  });

  testWidgets('Validation fails when required fields empty', (tester) async {
    final draft = ObservationDraft(id: '1', capturedAt: DateTime.now());
    await tester.pumpWidget(MaterialApp(home: ObservationDetailsScreen(draft: draft)));
    await tester.ensureVisible(find.text('Continue to Review'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Continue to Review'), warnIfMissed: false);
    await tester.pump();
    expect(find.text('Title is required'), findsOneWidget);
  });

  testWidgets('Form preserves category and priority', (tester) async {
    final draft = ObservationDraft(
      id: '1',
      capturedAt: DateTime.now(),
      category: ObservationCategory.safety,
      priority: ObservationPriority.critical,
    );
    await tester.pumpWidget(MaterialApp(home: ObservationDetailsScreen(draft: draft)));
    expect(find.text('Safety'), findsWidgets);
    expect(find.text('Critical'), findsWidgets);
  });
}
