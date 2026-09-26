// Placeholder service — no camera/mic/AI yet.
// Establishes architecture boundary for later phases.
class CaptureService {
  // Future: createObservation, attachPhoto, recordAudio,
  // transcribe, runOnDeviceModel, exportPdf.
  Future<void> placeholder() async {
    // Intentionally empty for Phase 2.
    await Future<void>.delayed(Duration.zero);
  }
}
