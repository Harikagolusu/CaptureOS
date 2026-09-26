import 'package:flutter_test/flutter_test.dart';
import 'package:phone/app/app.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('First launch shows join / create team', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const CaptureOSApp());
    await tester.pumpAndSettle();
    expect(find.text('CaptureOS'), findsWidgets);
    expect(find.text('Join a team'), findsOneWidget);
    expect(find.text('Create team'), findsOneWidget);
    expect(find.text('Team code'), findsOneWidget);

    await tester.tap(find.text('Create team'));
    await tester.pumpAndSettle();
    expect(find.text('Team name'), findsOneWidget);
    expect(find.text('Manager / team lead'), findsOneWidget);
  });

  testWidgets('Logged-in user lands on the tabs', (tester) async {
    SharedPreferences.setMockInitialValues({
      'base_url': 'http://127.0.0.1:1',
      'token': 't',
      'user': '{"id":1,"name":"Tej","role":"admin","profile":"manager"}',
      'team': '{"id":1,"name":"BuildX"}',
    });
    await tester.pumpWidget(const CaptureOSApp());
    await tester.pumpAndSettle();
    expect(find.text('CaptureOS'), findsWidgets);
    for (final tab in ['Assistant', 'Photos', 'Tasks']) {
      expect(find.text(tab), findsWidgets);
    }
  });
}
