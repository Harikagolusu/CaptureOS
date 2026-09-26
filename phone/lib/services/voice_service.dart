import 'dart:async';

// Frontend-only mock voice service. No real mic access this phase.
abstract class VoiceService {
  bool get isRecording;
  Stream<Duration> get durationStream;
  String? get lastError;
  Future<bool> hasPermission();
  Future<bool> startRecording(String path);
  Future<String?> stopRecording();
  Future<void> cancel();
  Future<void> dispose();
}

class VoiceServiceImpl implements VoiceService {
  bool _rec = false;
  String? _err;
  final _ctrl = StreamController<Duration>.broadcast();
  Timer? _t;
  Duration _d = Duration.zero;

  @override
  bool get isRecording => _rec;
  @override
  Stream<Duration> get durationStream => _ctrl.stream;
  @override
  String? get lastError => _err;

  @override
  Future<bool> hasPermission() async => true;

  @override
  Future<bool> startRecording(String path) async {
    _rec = true;
    _d = Duration.zero;
    _t?.cancel();
    _t = Timer.periodic(const Duration(seconds: 1), (_) {
      _d += const Duration(seconds: 1);
      _ctrl.add(_d);
    });
    return true;
  }

  @override
  Future<String?> stopRecording() async {
    _t?.cancel();
    _rec = false;
    return '/mock/voice_${DateTime.now().millisecondsSinceEpoch}.wav';
  }

  @override
  Future<void> cancel() async {
    _t?.cancel();
    _rec = false;
  }

  @override
  Future<void> dispose() async {
    _t?.cancel();
    await _ctrl.close();
  }
}

class FakeVoiceService implements VoiceService {
  FakeVoiceService({this.fakePath = '/tmp/fake.wav', this.shouldFail = false}) {
    lastError = shouldFail ? 'Fake error' : null;
  }
  final String fakePath;
  final bool shouldFail;
  bool _rec = false;
  final _c = StreamController<Duration>.broadcast();
  @override
  String? lastError;
  @override
  bool get isRecording => _rec;
  @override
  Stream<Duration> get durationStream => _c.stream;
  @override
  Future<bool> hasPermission() async => !shouldFail;
  @override
  Future<bool> startRecording(String path) async {
    if (shouldFail) return false;
    _rec = true;
    return true;
  }

  @override
  Future<String?> stopRecording() async {
    _rec = false;
    return shouldFail ? null : fakePath;
  }

  @override
  Future<void> cancel() async => _rec = false;
  @override
  Future<void> dispose() async => await _c.close();
}
