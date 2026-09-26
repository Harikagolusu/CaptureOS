import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';

import 'voice_service.dart';

/// Keeps recording while the user is in other apps: a microphone foreground service (required
/// "Recording" notification) plus a floating bubble drawn over other apps. Tapping the bubble or the
/// notification's Stop button ends the recording.
class BackgroundRecorder {
  BackgroundRecorder._();
  static final BackgroundRecorder instance = BackgroundRecorder._();

  final _voice = VoiceServiceImpl();
  final recording = ValueNotifier<bool>(false);
  final elapsed = ValueNotifier<Duration>(Duration.zero);

  /// Fires with the audio file path when a recording ends (from the app, bubble or notification).
  final _finished = StreamController<String>.broadcast();
  Stream<String> get finished => _finished.stream;

  StreamSubscription<Duration>? _tick;
  StreamSubscription<dynamic>? _bubble;
  bool _initialised = false;

  void _init() {
    if (_initialised) return;
    _initialised = true;
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'captureos_recording',
        channelName: 'Meeting recording',
        channelDescription: 'Shown while CaptureOS is recording a meeting.',
        onlyAlertOnce: true,
      ),
      iosNotificationOptions: const IOSNotificationOptions(showNotification: false, playSound: false),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.nothing(),
        autoRunOnBoot: false,
        allowWakeLock: true,
      ),
    );
    FlutterForegroundTask.addTaskDataCallback((data) {
      if (data == 'stop') stop();
    });
    _bubble = FlutterOverlayWindow.overlayListener.listen((event) {
      if (event == 'stop') stop();
    });
  }

  /// Asks once for "Display over other apps" (the floating bubble). Recording works without it.
  Future<bool> ensureBubblePermission() async {
    if (await FlutterOverlayWindow.isPermissionGranted()) return true;
    return await FlutterOverlayWindow.requestPermission() ?? false;
  }

  Future<String?> start() async {
    _init();
    await FlutterForegroundTask.requestNotificationPermission();
    final ok = await _voice.startRecording('');
    if (!ok) return _voice.lastError ?? 'Microphone permission denied';
    await FlutterForegroundTask.startService(
      serviceTypes: [ForegroundServiceTypes.microphone],
      serviceId: 701,
      notificationTitle: 'CaptureOS is recording',
      notificationText: 'Your meeting is being captured. Tap Stop when done.',
      notificationButtons: [const NotificationButton(id: 'stop', text: 'Stop')],
      callback: recorderServiceCallback,
    );
    if (await FlutterOverlayWindow.isPermissionGranted()) {
      await FlutterOverlayWindow.showOverlay(
        height: 180,
        width: 180,
        alignment: OverlayAlignment.centerRight,
        enableDrag: true,
        positionGravity: PositionGravity.auto,
        overlayTitle: 'CaptureOS recording',
        overlayContent: 'Tap the bubble to stop',
      );
    }
    elapsed.value = Duration.zero;
    _tick = _voice.durationStream.listen((d) => elapsed.value = d);
    recording.value = true;
    return null;
  }

  Future<String?> stop() async {
    if (!recording.value) return null;
    recording.value = false;
    await _tick?.cancel();
    final path = await _voice.stopRecording();
    await FlutterForegroundTask.stopService();
    if (await FlutterOverlayWindow.isActive()) await FlutterOverlayWindow.closeOverlay();
    if (path != null) _finished.add(path);
    return path;
  }

  Future<void> dispose() async {
    await _bubble?.cancel();
  }
}

/// Runs inside the foreground service. It only relays the notification's Stop button to the app;
/// the recording itself happens in the app process, which the service keeps alive.
@pragma('vm:entry-point')
void recorderServiceCallback() {
  FlutterForegroundTask.setTaskHandler(_RecorderTaskHandler());
}

class _RecorderTaskHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {}

  @override
  void onRepeatEvent(DateTime timestamp) {}

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {}

  @override
  void onNotificationButtonPressed(String id) {
    if (id == 'stop') FlutterForegroundTask.sendDataToMain('stop');
  }
}
