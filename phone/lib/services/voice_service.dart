import 'dart:async';

import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

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

/// Real microphone recording (record package), AAC in .m4a. Pass '' as path for a temp file.
class VoiceServiceImpl implements VoiceService {
  final _rec = AudioRecorder();
  final _ctrl = StreamController<Duration>.broadcast();
  bool _recording = false;
  String? _err;
  Timer? _t;
  Duration _d = Duration.zero;

  @override
  bool get isRecording => _recording;
  @override
  Stream<Duration> get durationStream => _ctrl.stream;
  @override
  String? get lastError => _err;

  @override
  Future<bool> hasPermission() => _rec.hasPermission();

  @override
  Future<bool> startRecording(String path) async {
    if (!await _rec.hasPermission()) {
      _err = 'Microphone permission denied';
      return false;
    }
    final target = path.isNotEmpty
        ? path
        : '${(await getTemporaryDirectory()).path}/rec_${DateTime.now().millisecondsSinceEpoch}.m4a';
    await _rec.start(const RecordConfig(encoder: AudioEncoder.aacLc, sampleRate: 16000, numChannels: 1), path: target);
    _recording = true;
    _err = null;
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
    _recording = false;
    return _rec.stop();
  }

  @override
  Future<void> cancel() async {
    _t?.cancel();
    _recording = false;
    await _rec.cancel();
  }

  @override
  Future<void> dispose() async {
    _t?.cancel();
    await _rec.dispose();
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
