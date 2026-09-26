import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/screens/queue_screen.dart';

void main() {
  testWidgets('QueueScreen shows waiting items', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: QueueScreen()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Offline Queue'), findsOneWidget);
    expect(find.textContaining('items waiting'), findsOneWidget);
    expect(find.text('Retry'), findsWidgets);
    expect(find.text('View'), findsWidgets);
  });

  testWidgets('QueueScreen shows mock pending', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: QueueScreen()));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 300));
    // Mock queue contains Draft / Needs Review (Pending is for queued status)
    expect(find.textContaining('Draft').evaluate().isNotEmpty || find.textContaining('Needs Review').evaluate().isNotEmpty || find.textContaining('Pending').evaluate().isNotEmpty, isTrue);
  });
}
