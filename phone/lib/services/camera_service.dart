// Frontend-only mock — no real camera access in this phase.
// Interface preserved for later hardware integration.
abstract class CameraService {
  bool get isInitialized;
  String? get lastError;
  Future<bool> initialize();
  Future<String?> capturePhoto();
  Future<void> dispose();
}

/// Mock implementation returns fake paths, simulates permission failures.
class CameraServiceImpl implements CameraService {
  // ignore: prefer_final_fields
  bool _init = true;
  @override
  bool get isInitialized => _init;
  @override
  String? get lastError => null;
  @override
  Future<bool> initialize() async => true;
  @override
  Future<String?> capturePhoto() async => '/mock/photo_${DateTime.now().millisecondsSinceEpoch}.jpg';
  @override
  Future<void> dispose() async {}
}

class FakeCameraService implements CameraService {
  FakeCameraService({this.fakePath = '/tmp/fake.jpg', this.shouldFail = false}) {
    lastError = shouldFail ? 'Fake init failed' : null;
  }
  final String fakePath;
  final bool shouldFail;
  bool _init = false;
  @override
  String? lastError;
  @override
  bool get isInitialized => _init && !shouldFail;
  @override
  Future<bool> initialize() async {
    _init = !shouldFail;
    lastError = shouldFail ? 'Fake init failed' : null;
    return _init;
  }

  @override
  Future<String?> capturePhoto() async => shouldFail ? null : fakePath;
  @override
  Future<void> dispose() async => _init = false;
}
