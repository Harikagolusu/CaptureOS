import 'package:flutter_test/flutter_test.dart';
import 'package:phone/app/app.dart';

void main() {
  testWidgets('CaptureOS home shows title and Start Capture', (tester) async {
    await tester.pumpWidget(const CaptureOSApp());
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('CaptureOS'), findsWidgets);
    expect(find.text('Start Capture'), findsOneWidget);
    expect(find.text('Capture → Understand → Review → Evidence'), findsOneWidget);
  });
}
