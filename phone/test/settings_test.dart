import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/screens/settings_screen.dart';

void main() {
  testWidgets('Settings shows Appearance and toggles', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SettingsScreen()));
    expect(find.text('Appearance'), findsOneWidget);
    expect(find.text('System'), findsOneWidget);
    expect(find.text('Light'), findsOneWidget);
    expect(find.text('Dark'), findsOneWidget);
    expect(find.text('Notifications'), findsOneWidget);
    expect(find.text('Offline Mode'), findsOneWidget);
    expect(find.text('About'), findsOneWidget);
  });

  testWidgets('Settings toggles switch', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SettingsScreen()));
    final switches = find.byType(SwitchListTile);
    expect(switches, findsNWidgets(2));
    await tester.tap(switches.first);
    await tester.pump();
  });
}
