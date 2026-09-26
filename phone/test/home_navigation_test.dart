import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/screens/capture_screen.dart';
import 'package:phone/screens/home_screen.dart';

void main() {
  testWidgets('Home Start Capture navigates to CaptureScreen', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: HomeScreen()));
    expect(find.text('Start Capture'), findsOneWidget);
    await tester.tap(find.text('Start Capture'));
    await tester.pumpAndSettle();
    expect(find.byType(CaptureScreen), findsOneWidget);
    expect(find.text('Field Capture'), findsOneWidget);
  });

  testWidgets('Home shows Capture pipeline and tagline', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: HomeScreen()));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Offline Ready'), findsOneWidget);
    expect(find.text('Capture → Understand → Review → Evidence'), findsOneWidget);
    expect(find.text('Recent observations'), findsOneWidget);
  });
}
