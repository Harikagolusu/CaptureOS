import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/models/observation.dart';
import 'package:phone/screens/confirmation_screen.dart';

void main() {
  testWidgets('Confirmation shows approved and back to home', (tester) async {
    final draft = ObservationDraft(id: '1', capturedAt: DateTime.now(), photoPath: '/mock/p.jpg', status: ObservationStatus.approved);
    await tester.pumpWidget(MaterialApp(home: ConfirmationScreen(draft: draft)));
    expect(find.text('Observation Approved'), findsOneWidget);
    expect(find.text('Back to Home'), findsOneWidget);
    expect(find.textContaining('Evidence'), findsWidgets);
  });
}
