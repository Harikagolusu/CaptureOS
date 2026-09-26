import 'package:image_picker/image_picker.dart';

abstract class CameraService {
  bool get isInitialized;
  String? get lastError;
  Future<bool> initialize();
  Future<String?> capturePhoto();
  Future<void> dispose();
}

/// Real camera through the system camera app (image_picker).
class CameraServiceImpl implements CameraService {
  final _picker = ImagePicker();
  String? _error;
  @override
  bool get isInitialized => true;
  @override
  String? get lastError => _error;
  @override
  Future<bool> initialize() async => true;
  @override
  Future<String?> capturePhoto() async {
    try {
      final shot = await _picker.pickImage(source: ImageSource.camera, maxWidth: 2048, imageQuality: 85);
      _error = shot == null ? 'No photo taken' : null;
      return shot?.path;
    } catch (e) {
      _error = 'Camera error: $e';
      return null;
    }
  }

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
