import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../services/voice_service.dart';

/// Records a meeting and uploads it. Pops with the new meeting id.
class RecordMeetingScreen extends StatefulWidget {
  const RecordMeetingScreen({super.key, this.voiceService});

  final VoiceService? voiceService;

  @override
  State<RecordMeetingScreen> createState() => _RecordMeetingScreenState();
}

class _RecordMeetingScreenState extends State<RecordMeetingScreen> {
  late final VoiceService _voice = widget.voiceService ?? VoiceServiceImpl();
  final _title = TextEditingController();
  StreamSubscription<Duration>? _sub;
  Duration _elapsed = Duration.zero;
  bool _recording = false;
  bool _uploading = false;
  String _language = '';

  @override
  void initState() {
    super.initState();
    _sub = _voice.durationStream.listen((d) => setState(() => _elapsed = d));
  }

  @override
  void dispose() {
    _sub?.cancel();
    _voice.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    final ok = await _voice.startRecording('');
    if (!ok) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(_voice.lastError ?? 'Cannot record')));
      return;
    }
    setState(() {
      _recording = true;
      _elapsed = Duration.zero;
    });
  }

  Future<void> _stopAndUpload() async {
    final path = await _voice.stopRecording();
    setState(() {
      _recording = false;
      _uploading = true;
    });
    if (path == null) {
      setState(() => _uploading = false);
      return;
    }
    try {
      final r = await ApiClient.instance.upload('/meetings', {
        'audio': [path]
      }, fields: {
        'title': _title.text.trim(),
        'language': _language,
      }) as Map;
      if (mounted) Navigator.of(context).pop(r['id'] as int);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _uploading = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Upload failed: ${e.message}')));
    }
  }

  String _fmt(Duration d) =>
      '${d.inMinutes.toString().padLeft(2, '0')}:${d.inSeconds.remainder(60).toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return PopScope(
      canPop: !_recording && !_uploading,
      child: Scaffold(
        appBar: AppBar(title: const Text('Record meeting')),
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                TextField(
                  controller: _title,
                  enabled: !_uploading,
                  decoration: const InputDecoration(labelText: 'Title (optional)', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                Wrap(spacing: 8, children: [
                  for (final l in const {'': 'Auto', 'te': 'Telugu', 'hi': 'Hindi', 'en': 'English'}.entries)
                    ChoiceChip(label: Text(l.value), selected: _language == l.key, onSelected: (_) => setState(() => _language = l.key)),
                ]),
                const Spacer(),
                Text(_fmt(_elapsed), style: text.displayMedium?.copyWith(fontFeatures: const [FontFeature.tabularFigures()])),
                const SizedBox(height: 8),
                Text(
                  _uploading
                      ? 'Uploading… the AI works on it next'
                      : _recording
                          ? 'Recording — keep the phone near the speakers'
                          : 'Tap to start. Say names ("Kiran, what\'s the update?") so the AI knows who is who.',
                  textAlign: TextAlign.center,
                  style: text.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                ),
                const Spacer(),
                SizedBox(
                  width: 96,
                  height: 96,
                  child: _uploading
                      ? const CircularProgressIndicator()
                      : FloatingActionButton.large(
                          heroTag: 'rec',
                          backgroundColor: _recording ? scheme.error : scheme.primary,
                          foregroundColor: _recording ? scheme.onError : scheme.onPrimary,
                          onPressed: _recording ? _stopAndUpload : _start,
                          child: Icon(_recording ? Icons.stop : Icons.mic, size: 40),
                        ),
                ),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
