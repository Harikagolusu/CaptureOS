import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/share.dart';
import '../models/api_models.dart';
import '../widgets/error_state.dart';
import '../widgets/loading_state.dart';

/// Meeting result. Polls while the backend is still transcribing / thinking.
class MeetingDetailScreen extends StatefulWidget {
  const MeetingDetailScreen({super.key, required this.meetingId});

  final int meetingId;

  @override
  State<MeetingDetailScreen> createState() => _MeetingDetailScreenState();
}

class _MeetingDetailScreenState extends State<MeetingDetailScreen> {
  final api = ApiClient.instance;
  MeetingDetail? _m;
  Object? _error;
  Timer? _poll;
  bool _mineOnly = false;

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
      final m = MeetingDetail.fromJson(Map<String, dynamic>.from(await api.get('/meetings/${widget.meetingId}') as Map));
      if (!mounted) return;
      setState(() {
        _m = m;
        _error = null;
      });
      _poll?.cancel();
      if (m.processing) _poll = Timer(const Duration(seconds: 3), _load);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  int? get _myId => api.user?['id'] as int?;

  String _notesText(MeetingDetail m) {
    final mine = m.tasks.where((t) => t.ownerUserId == _myId).toList();
    final b = StringBuffer('${m.title}\n\n${m.summary}\n');
    if (mine.isNotEmpty) {
      b.writeln('\nMy tasks:');
      for (final t in mine) {
        b.writeln('☐ ${t.title}${t.due != null ? ' (due ${t.due})' : ''}');
      }
    }
    final others = m.tasks.where((t) => t.ownerUserId != _myId).toList();
    if (others.isNotEmpty) {
      b.writeln('\nTeam tasks:');
      for (final t in others) {
        b.writeln('• ${t.title} → ${t.owner}${t.due != null ? ' (due ${t.due})' : ''}');
      }
    }
    b.write('\n— CaptureOS');
    return b.toString();
  }

  Future<void> _answer(Question q) async {
    final c = TextEditingController();
    final answer = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Answer'),
        content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(q.text),
          const SizedBox(height: 12),
          TextField(controller: c, autofocus: true, decoration: const InputDecoration(border: OutlineInputBorder())),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, c.text.trim()), child: const Text('Save')),
        ],
      ),
    );
    if (answer == null || answer.isEmpty) return;
    try {
      await api.send('POST', '/questions/${q.id}/answer', {'answer': answer});
      _load();
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final m = _m;
    return Scaffold(
      appBar: AppBar(
        title: Text(m == null || m.title.isEmpty ? 'Meeting' : m.title),
        actions: [
          if (m != null && m.notionUrl.isNotEmpty)
            IconButton(tooltip: 'Open in Notion', icon: const Icon(Icons.open_in_new), onPressed: () => openLink(m.notionUrl)),
        ],
      ),
      floatingActionButton: m == null || m.processing
          ? null
          : FloatingActionButton.extended(
              onPressed: () => shareText(_notesText(m), subject: m.title),
              icon: const Icon(Icons.note_add_outlined),
              label: const Text('Save to Notes'),
            ),
      body: () {
        if (_error != null && m == null) return ErrorState(message: _error.toString(), onRetry: _load);
        if (m == null) return const LoadingState();
        if (m.processing) return _Processing(status: m.status);
        if (m.status == 'error') return ErrorState(message: 'Processing failed: ${m.error}', onRetry: _load);
        return _body(context, m);
      }(),
    );
  }

  Widget _body(BuildContext context, MeetingDetail m) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    final me = m.attendees.where((a) => a.userId == _myId).firstOrNull;
    final tasks = _mineOnly ? m.tasks.where((t) => t.ownerUserId == _myId).toList() : m.tasks;
    final segments = _mineOnly ? m.segments.where((s) => s.userId == _myId).toList() : m.segments;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
        children: [
          SegmentedButton<bool>(
            segments: const [
              ButtonSegment(value: false, label: Text('Everyone'), icon: Icon(Icons.groups_outlined)),
              ButtonSegment(value: true, label: Text('Just me'), icon: Icon(Icons.person_outline)),
            ],
            selected: {_mineOnly},
            onSelectionChanged: (s) => setState(() => _mineOnly = s.first),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Summary', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                const SizedBox(height: 6),
                Text(m.summary),
                if (me != null && me.update.isNotEmpty) ...[
                  const Divider(height: 24),
                  Text('My update', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 4),
                  Text(me.update),
                ],
              ]),
            ),
          ),
          if (!_mineOnly && m.attendees.isNotEmpty) ...[
            _Header('Who was there'),
            for (final a in m.attendees)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: CircleAvatar(child: Text(a.name.isEmpty ? '?' : a.name[0].toUpperCase())),
                title: Text(a.name + (a.speakerLabel.isNotEmpty ? '  ·  ${a.speakerLabel}' : '')),
                subtitle: a.update.isEmpty ? null : Text(a.update),
              ),
          ],
          _Header(_mineOnly ? 'My tasks' : 'Tasks'),
          if (tasks.isEmpty) Text('No tasks', style: TextStyle(color: scheme.outline)),
          for (final t in tasks)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.check_box_outline_blank, color: t.priority == 'high' ? scheme.error : null),
              title: Text(t.title),
              subtitle: Text('${t.owner}${t.due != null ? ' · due ${t.due}' : ''} · ${t.priority}'),
            ),
          if (!_mineOnly && m.questions.isNotEmpty) ...[
            _Header('Questions from the AI'),
            for (final q in m.questions)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(q.status == 'answered' ? Icons.check_circle : Icons.help_outline, color: scheme.primary),
                title: Text(q.text),
                subtitle: q.answer.isEmpty ? null : Text('→ ${q.answer}'),
                trailing: q.status == 'open' && api.isAdmin ? TextButton(onPressed: () => _answer(q), child: const Text('Answer')) : null,
              ),
          ],
          _Header(_mineOnly ? 'What I said' : 'Transcript'),
          for (final s in segments)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: RichText(
                text: TextSpan(style: text.bodyMedium, children: [
                  TextSpan(text: '${s.who}: ', style: const TextStyle(fontWeight: FontWeight.w700)),
                  TextSpan(text: s.text),
                ]),
              ),
            ),
        ],
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header(this.label);
  final String label;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 20, bottom: 6),
        child: Text(label.toUpperCase(),
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                fontWeight: FontWeight.w700, letterSpacing: 0.8, color: Theme.of(context).colorScheme.primary)),
      );
}

class _Processing extends StatelessWidget {
  const _Processing({required this.status});
  final String status;
  @override
  Widget build(BuildContext context) {
    const steps = ['uploaded', 'transcribing', 'thinking', 'done'];
    final at = steps.indexOf(status);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const CircularProgressIndicator(),
          const SizedBox(height: 24),
          for (var i = 0; i < 3; i++)
            ListTile(
              leading: Icon(i < at ? Icons.check_circle : (i == at ? Icons.autorenew : Icons.radio_button_unchecked)),
              title: Text(const ['Uploaded', 'Transcribing speech', 'AI finding tasks & owners'][i]),
            ),
        ]),
      ),
    );
  }
}
