import 'package:flutter/material.dart';
import '../models/observation.dart';
import '../services/camera_service.dart';
import '../services/voice_service.dart';
import 'observation_details_screen.dart';
import 'review_screen.dart';

class CaptureScreen extends StatefulWidget {
  const CaptureScreen({
    super.key,
    this.cameraService,
    this.voiceService,
  });

  final CameraService? cameraService;
  final VoiceService? voiceService;

  @override
  State<CaptureScreen> createState() => _CaptureScreenState();
}

class _CaptureScreenState extends State<CaptureScreen> {
  late final CameraService _camera;
  late final VoiceService _voice;
  bool _cameraReady = false;
  String? _cameraError;
  String? _photoPath;
  String? _audioPath;
  bool _isRecording = false;
  Duration _recordDuration = Duration.zero;

  @override
  void initState() {
    super.initState();
    _camera = widget.cameraService ?? CameraServiceImpl();
    _voice = widget.voiceService ?? VoiceServiceImpl();
    _initCamera();
    _voice.durationStream.listen((d) {
      if (mounted) setState(() => _recordDuration = d);
    });
  }

  Future<void> _initCamera() async {
    final ok = await _camera.initialize();
    if (!mounted) return;
    setState(() {
      _cameraReady = ok;
      _cameraError = _camera.lastError;
    });
  }

  @override
  void dispose() {
    _camera.dispose();
    _voice.dispose();
    super.dispose();
  }

  Future<void> _capturePhoto() async {
    final path = await _camera.capturePhoto();
    if (path == null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_camera.lastError ?? 'Capture failed')),
      );
      return;
    }
    setState(() => _photoPath = path);
  }

  Future<void> _toggleRecording() async {
    if (_isRecording) {
      final path = await _voice.stopRecording();
      if (!mounted) return;
      setState(() {
        _isRecording = false;
        if (path != null) _audioPath = path;
      });
      return;
    }
    final ok = await _voice.startRecording('/mock/voice.wav');
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_voice.lastError ?? 'Recording failed')),
      );
      return;
    }
    setState(() {
      _isRecording = true;
      _recordDuration = Duration.zero;
    });
  }

  void _retake() => setState(() => _photoPath = null);

  Future<void> _continueToReview() async {
    if (_photoPath == null && _audioPath == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Capture a photo or voice note to continue.')),
      );
      return;
    }
    var draft = ObservationDraft(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      capturedAt: DateTime.now(),
      photoPath: _photoPath,
      audioPath: _audioPath,
      title: 'Field observation',
      status: ObservationStatus.draft,
    );
    final details = await Navigator.of(context).push<ObservationDraft>(
      MaterialPageRoute(builder: (_) => ObservationDetailsScreen(draft: draft)),
    );
    if (details != null) draft = details;
    if (!mounted) return;
    // details screen already set needsReview status; push review
    if (details != null) {
      Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => ReviewScreen(draft: draft)),
      );
    }
  }

  String _formatDuration(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Field Capture'),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.of(context).pop(),
          tooltip: 'Close',
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _SectionHeader(
                icon: Icons.photo_camera_outlined,
                label: 'PHOTO',
                trailing: _photoPath != null
                    ? Chip(label: const Text('Captured'), backgroundColor: scheme.primaryContainer)
                    : null,
              ),
              const SizedBox(height: 10),
              _buildPhotoArea(scheme),
              const SizedBox(height: 12),
              if (_photoPath != null)
                Row(
                  children: [
                    OutlinedButton.icon(
                      onPressed: _retake,
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Retake'),
                    ),
                    const SizedBox(width: 12),
                    Text('Photo ready',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: scheme.primary,
                              fontWeight: FontWeight.w600,
                            )),
                  ],
                )
              else
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: _cameraReady ? _capturePhoto : null,
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Capture photo'),
                  ),
                ),
              if (_cameraError != null) ...[
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: scheme.errorContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(_cameraError!, style: TextStyle(color: scheme.onErrorContainer, fontSize: 13)),
                ),
              ],
              const SizedBox(height: 20),
              _SectionHeader(
                icon: Icons.mic_outlined,
                label: 'VOICE NOTE',
                trailing: _audioPath != null
                    ? Chip(label: const Text('Saved'), backgroundColor: scheme.primaryContainer)
                    : (_isRecording
                        ? Chip(
                            label: Text(_formatDuration(_recordDuration)),
                            backgroundColor: scheme.errorContainer,
                          )
                        : null),
              ),
              const SizedBox(height: 10),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: _isRecording ? scheme.errorContainer.withValues(alpha: 0.3) : scheme.surfaceContainer,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: scheme.outlineVariant),
                ),
                child: Row(
                  children: [
                    Icon(_isRecording ? Icons.fiber_manual_record : Icons.mic_none,
                        color: _isRecording ? scheme.error : scheme.primary),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _isRecording
                                ? 'Recording... ${_formatDuration(_recordDuration)}'
                                : _audioPath != null
                                    ? 'Voice note saved'
                                    : 'Tap to record a short note',
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                          ),
                          Text(
                            _audioPath != null ? 'mock_voice.wav' : 'On-device only, no cloud',
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                          ),
                        ],
                      ),
                    ),
                    if (_isRecording)
                      Container(width: 12, height: 12, decoration: BoxDecoration(color: scheme.error, shape: BoxShape.circle)),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _toggleRecording,
                  icon: Icon(_isRecording ? Icons.stop : Icons.mic),
                  label: Text(_isRecording ? 'Stop recording' : (_audioPath == null ? 'Start recording' : 'Record again')),
                  style: FilledButton.styleFrom(
                    backgroundColor: _isRecording ? scheme.error : scheme.primary,
                    foregroundColor: _isRecording ? scheme.onError : scheme.onPrimary,
                    minimumSize: const Size.fromHeight(52),
                  ),
                ),
              ),
              if (_audioPath != null && !_isRecording) ...[
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: () => setState(() => _audioPath = null),
                  icon: const Icon(Icons.delete_outline, size: 18),
                  label: const Text('Remove voice note'),
                ),
              ],
              const SizedBox(height: 20),
              _SectionHeader(icon: Icons.fact_check_outlined, label: 'REVIEW'),
              const SizedBox(height: 10),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text('Human approval required. Review on next screen.',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: _continueToReview,
                  icon: const Icon(Icons.arrow_forward),
                  label: const Text('Continue to review'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPhotoArea(ColorScheme scheme) {
    if (_photoPath != null) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: AspectRatio(
          aspectRatio: 4 / 3,
          child: Container(
            color: scheme.primaryContainer,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.check_circle, size: 48, color: scheme.primary),
                const SizedBox(height: 8),
                Text('Mock photo captured', style: TextStyle(color: scheme.onPrimaryContainer, fontWeight: FontWeight.w600)),
                Text('mock_photo.jpg', style: TextStyle(color: scheme.onPrimaryContainer, fontSize: 12)),
              ],
            ),
          ),
        ),
      );
    }

    if (!_cameraReady) {
      return Container(
        height: 220,
        decoration: BoxDecoration(
          color: scheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: scheme.outlineVariant),
        ),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.videocam_off_outlined, size: 32, color: scheme.outline),
              const SizedBox(height: 8),
              Text(_cameraError ?? 'Initializing camera...',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                  textAlign: TextAlign.center),
            ],
          ),
        ),
      );
    }

    // Frontend-only placeholder instead of real CameraPreview
    return ClipRRect(
      borderRadius: BorderRadius.circular(14),
      child: AspectRatio(
        aspectRatio: 4 / 3,
        child: Container(
          color: scheme.surfaceContainerHighest,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.camera_alt_outlined, size: 48, color: scheme.outline),
              const SizedBox(height: 8),
              Text('Camera Preview Placeholder',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant)),
              Text('Tap Capture photo to simulate capture',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.outline, fontSize: 11)),
            ],
          ),
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.icon, required this.label, this.trailing});
  final IconData icon;
  final String label;
  final Widget? trailing;
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Row(
      children: [
        Icon(icon, size: 16, color: scheme.primary),
        const SizedBox(width: 6),
        Text(label,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.8,
                  color: scheme.primary,
                )),
        const Spacer(),
        if (trailing case final Widget t) t,
      ],
    );
  }
}
