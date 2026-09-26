import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phone/screens/capture_screen.dart';
import 'package:phone/services/camera_service.dart';
import 'package:phone/services/voice_service.dart';

void main() {
  testWidgets('CaptureScreen renders PHOTO VOICE REVIEW sections', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: CaptureScreen(
          cameraService: FakeCameraService(),
          voiceService: FakeVoiceService(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('PHOTO'), findsOneWidget);
    expect(find.text('VOICE NOTE'), findsOneWidget);
    expect(find.text('REVIEW'), findsOneWidget);
    expect(find.text('Capture photo'), findsOneWidget);
    expect(find.text('Start recording'), findsOneWidget);
    expect(find.text('Continue to review'), findsOneWidget);
  });

  testWidgets('CaptureScreen shows error when camera fails', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: CaptureScreen(
          cameraService: FakeCameraService(shouldFail: true),
          voiceService: FakeVoiceService(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.textContaining('Fake init failed'), findsWidgets);
  });

  testWidgets('CaptureScreen capture button is 48dp+ touch target', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: CaptureScreen(
          cameraService: FakeCameraService(),
          voiceService: FakeVoiceService(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    final btn = tester.getSize(find.widgetWithText(ElevatedButton, 'Capture photo'));
    expect(btn.height, greaterThanOrEqualTo(48));
  });
}
