import 'dart:async';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../core/api_client.dart';
import '../core/share.dart';
import '../models/api_models.dart';
import '../widgets/auth_image.dart';
import '../widgets/error_state.dart';
import '../widgets/loading_state.dart';
import 'capture_screen.dart';

/// One site visit: add observations (photo + voice), then compile the report.
class FieldVisitScreen extends StatefulWidget {
  const FieldVisitScreen({super.key, required this.visitId});

  final int visitId;

  @override
  State<FieldVisitScreen> createState() => _FieldVisitScreenState();
}

class _FieldVisitScreenState extends State<FieldVisitScreen> {
  final api = ApiClient.instance;
  FieldVisit? _v;
  Object? _error;
  Timer? _poll;
  bool _uploading = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _poll?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final v = FieldVisit.fromJson(Map<String, dynamic>.from(await api.get('/field/visits/${widget.visitId}') as Map));
      if (!mounted) return;
      setState(() {
        _v = v;
        _error = null;
      });
      _poll?.cancel();
      if (v.reportStatus == 'building' || v.observations.any((o) => o.processing)) {
        _poll = Timer(const Duration(seconds: 3), _load);
      }
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  Future<void> _addObservation() async {
    final result = await Navigator.of(context).push<CaptureResult>(
      MaterialPageRoute(builder: (_) => const CaptureScreen(returnResult: true, title: 'Add observation')),
    );
    if (result?.photoPath == null) return;
    setState(() => _uploading = true);
    try {
      await api.upload('/field/visits/${widget.visitId}/observations', {
        'photo': [result!.photoPath!],
        if (result.audioPath != null) 'voice': [result.audioPath!],
      });
      await _load();
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _compile() async {
    await api.send('POST', '/field/visits/${widget.visitId}/report');
    _load();
  }

  Future<void> _shareReport(FieldVisit v) async {
    final dir = await getTemporaryDirectory();
    final file = await api.download(v.reportUrl, '${dir.path}/site-report-${v.id}.docx');
    await shareFile(file.path, text: 'Site report: ${v.site}');
  }

  @override
  Widget build(BuildContext context) {
    final v = _v;
    return Scaffold(
      appBar: AppBar(title: Text(v?.site ?? 'Site visit')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _uploading ? null : _addObservation,
        icon: _uploading ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.add_a_photo_outlined),
        label: const Text('Add observation'),
      ),
      body: () {
        if (_error != null && v == null) return ErrorState(message: _error.toString(), onRetry: _load);
        if (v == null) return const LoadingState();
        return _body(context, v);
      }(),
    );
  }

  Widget _body(BuildContext context, FieldVisit v) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    final done = v.observations.where((o) => o.status == 'done').length;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
        children: [
          if (v.reportStatus == 'done')
            Card(
              color: scheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Report', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 6),
                  Text(v.summary),
                  if (v.keyIssues.isNotEmpty) ...[const SizedBox(height: 8), for (final i in v.keyIssues) Text('• $i')],
                  if (v.recommendations.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text('Next steps', style: text.labelLarge),
                    for (final r in v.recommendations) Text('→ $r'),
                  ],
                  const SizedBox(height: 10),
                  Wrap(spacing: 8, children: [
                    FilledButton.icon(onPressed: () => _shareReport(v), icon: const Icon(Icons.description_outlined), label: const Text('Word report')),
                    if (v.notionUrl.isNotEmpty)
                      OutlinedButton.icon(onPressed: () => openLink(v.notionUrl), icon: const Icon(Icons.open_in_new), label: const Text('Notion')),
                  ]),
                ]),
              ),
            )
          else
            FilledButton.icon(
              onPressed: done == 0 || v.reportStatus == 'building' ? null : _compile,
              icon: v.reportStatus == 'building'
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.summarize_outlined),
              label: Text(v.reportStatus == 'building' ? 'Building report…' : 'Compile report ($done observations)'),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
            ),
          if (v.reportStatus == 'error') Text('Report failed: ${v.reportError}', style: TextStyle(color: scheme.error)),
          const SizedBox(height: 16),
          for (final o in v.observations)
            Card(
              clipBehavior: Clip.antiAlias,
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                AspectRatio(aspectRatio: 16 / 9, child: AuthImage(o.photoUrl)),
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: o.processing
                      ? const Row(children: [
                          SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                          SizedBox(width: 10),
                          Text('AI is looking at this photo…'),
                        ])
                      : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Row(children: [
                            Expanded(child: Text(o.title, style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700))),
                            _Severity(o.severity),
                          ]),
                          const SizedBox(height: 4),
                          Text(o.observation),
                          if (o.action.isNotEmpty) ...[
                            const SizedBox(height: 6),
                            Text('Action: ${o.action}${o.owner.isNotEmpty ? ' (${o.owner})' : ''}', style: TextStyle(color: scheme.primary)),
                          ],
                          if (o.voiceText.isNotEmpty) ...[
                            const SizedBox(height: 6),
                            Text('🎙 ${o.voiceText}', style: text.bodySmall?.copyWith(fontStyle: FontStyle.italic)),
                          ],
                          if (o.error.isNotEmpty) Text(o.error, style: TextStyle(color: scheme.error)),
                        ]),
                ),
              ]),
            ),
        ],
      ),
    );
  }
}

class _Severity extends StatelessWidget {
  const _Severity(this.level);
  final String level;
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = switch (level) {
      'high' => scheme.error,
      'medium' => Colors.orange,
      'low' => Colors.amber,
      _ => Colors.green,
    };
    return Chip(
      label: Text(level.isEmpty ? '—' : level, style: const TextStyle(color: Colors.white, fontSize: 12)),
      backgroundColor: color,
      visualDensity: VisualDensity.compact,
      side: BorderSide.none,
    );
  }
}
