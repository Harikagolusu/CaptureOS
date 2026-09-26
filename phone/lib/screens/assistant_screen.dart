import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api_client.dart';
import '../models/api_models.dart';
import '../services/background_recorder.dart';
import '../services/destinations.dart';
import 'meeting_detail_screen.dart';
import 'team_screen.dart';

/// Chat-style assistant: record a meeting (keeps going in other apps, with a floating bubble) or
/// type a note; the AI replies with the summary and tasks, which go to Notion, the calendar and Notes.
class AssistantScreen extends StatefulWidget {
  const AssistantScreen({super.key, required this.onLogout});

  final VoidCallback onLogout;

  @override
  State<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends State<AssistantScreen> {
  final api = ApiClient.instance;
  final rec = BackgroundRecorder.instance;
  final _input = TextEditingController();
  final _scroll = ScrollController();
  List<MeetingSummary> _items = [];
  Object? _error;
  bool _loading = true;
  bool _sending = false;
  Timer? _poll;
  StreamSubscription<String>? _finishedSub;
  final Map<int, String> _outcome = {}; // meeting id -> "Calendar +2 · Notes"

  @override
  void initState() {
    super.initState();
    _finishedSub = rec.finished.listen(_upload);
    _load();
  }

  @override
  void dispose() {
    _poll?.cancel();
    _finishedSub?.cancel();
    super.dispose();
  }

  int? get _myId => api.user?['id'] as int?;

  Future<void> _load() async {
    try {
      final rows = await api.get('/meetings') as List;
      final items = rows.map((e) => MeetingSummary.fromJson(Map<String, dynamic>.from(e as Map))).toList().reversed.toList();
      if (!mounted) return;
      setState(() {
        _items = items;
        _loading = false;
        _error = null;
      });
      _jumpToEnd();
      await _handleFinished(items);
      _poll?.cancel();
      if (items.any((m) => m.processing)) _poll = Timer(const Duration(seconds: 3), _load);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  /// First time a meeting of mine finishes: tasks with dates -> calendar, then offer Notes.
  Future<void> _handleFinished(List<MeetingSummary> items) async {
    final prefs = await SharedPreferences.getInstance();
    final handled = (prefs.getStringList('handled_meetings') ?? []).toSet();
    if (!prefs.containsKey('handled_meetings')) {
      // Fresh install: don't replay old meetings.
      await prefs.setStringList('handled_meetings', items.map((m) => '${m.id}').toList());
      return;
    }
    for (final m in items.where((m) => m.status == 'done' && m.hostId == _myId && !handled.contains('${m.id}'))) {
      handled.add('${m.id}');
      await prefs.setStringList('handled_meetings', handled.toList());
      var added = 0;
      try {
        added = await Destinations.syncMyTasksToCalendar();
      } catch (_) {}
      setState(() => _outcome[m.id] = [if (added > 0) 'Calendar +$added', 'Notion'].join(' · '));
      if (mounted) await Destinations.shareToNotes(m.title, m.summary, m.tasks);
    }
  }

  void _jumpToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
    });
  }

  Future<void> _sendText() async {
    final text = _input.text.trim();
    if (text.isEmpty) return;
    setState(() => _sending = true);
    try {
      await api.send('POST', '/meetings/text', {'text': text});
      _input.clear();
      await _load();
    } on ApiException catch (e) {
      _snack(e.message);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _toggleRecording() async {
    if (rec.recording.value) {
      await rec.stop(); // upload happens via the finished stream
      return;
    }
    await rec.ensureBubblePermission();
    final err = await rec.start();
    if (err != null) _snack(err);
  }

  Future<void> _upload(String path) async {
    setState(() => _sending = true);
    try {
      await api.upload('/meetings', {'audio': [path]});
      await _load();
    } on ApiException catch (e) {
      _snack('Upload failed: ${e.message}');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _retry(MeetingSummary m) async {
    try {
      await api.send('POST', '/meetings/${m.id}/retry');
      await _load();
    } on ApiException catch (e) {
      _snack(e.message);
    }
  }

  void _snack(String msg) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('CaptureOS', style: text.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
          Text('Record a meeting or tell me what to do', style: text.labelSmall),
        ]),
        actions: [
          IconButton(
            tooltip: 'Team & settings',
            icon: CircleAvatar(radius: 16, child: Text(api.userName.isEmpty ? '?' : api.userName[0])),
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => TeamScreen(onLogout: widget.onLogout))),
          ),
        ],
      ),
      body: Column(children: [
        Expanded(child: _messages(context)),
        _recordingBar(context),
        _composer(context),
      ]),
    );
  }

  Widget _messages(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(child: TextButton.icon(onPressed: _load, icon: const Icon(Icons.refresh), label: Text('$_error\nTap to retry')));
    }
    if (_items.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Text(
            'Tap the mic to record a meeting — it keeps recording while you use other apps.\n\n'
            'Or type: "Remind Kiran to send the invoice by Friday".',
            textAlign: TextAlign.center,
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        controller: _scroll,
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
        children: [
          for (final m in _items) ...[
            _UserBubble(m),
            _AssistantBubble(
              m,
              outcome: _outcome[m.id],
              onOpen: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => MeetingDetailScreen(meetingId: m.id))),
              onRetry: () => _retry(m),
              onNotes: () => Destinations.shareToNotes(m.title, m.summary, m.tasks),
            ),
          ],
        ],
      ),
    );
  }

  Widget _recordingBar(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ValueListenableBuilder<bool>(
      valueListenable: rec.recording,
      builder: (_, on, _) => !on
          ? const SizedBox.shrink()
          : Container(
              color: scheme.errorContainer,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(children: [
                Icon(Icons.fiber_manual_record, color: scheme.error),
                const SizedBox(width: 8),
                ValueListenableBuilder<Duration>(
                  valueListenable: rec.elapsed,
                  builder: (_, d, _) => Text(
                    'Recording ${d.inMinutes.toString().padLeft(2, '0')}:${d.inSeconds.remainder(60).toString().padLeft(2, '0')}'
                    ' — you can switch apps',
                    style: TextStyle(color: scheme.onErrorContainer, fontWeight: FontWeight.w600),
                  ),
                ),
              ]),
            ),
    );
  }

  Widget _composer(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 6, 12, 10),
        child: Row(children: [
          Expanded(
            child: TextField(
              controller: _input,
              minLines: 1,
              maxLines: 4,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(
                hintText: 'Type a task or note…',
                filled: true,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(24), borderSide: BorderSide.none),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                suffixIcon: IconButton(onPressed: _sending ? null : _sendText, icon: const Icon(Icons.send)),
              ),
              onSubmitted: (_) => _sendText(),
            ),
          ),
          const SizedBox(width: 8),
          ValueListenableBuilder<bool>(
            valueListenable: rec.recording,
            builder: (_, on, _) => FloatingActionButton(
              heroTag: 'mic',
              onPressed: _sending ? null : _toggleRecording,
              backgroundColor: on ? scheme.error : scheme.primary,
              foregroundColor: on ? scheme.onError : scheme.onPrimary,
              child: _sending ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2)) : Icon(on ? Icons.stop : Icons.mic),
            ),
          ),
        ]),
      ),
    );
  }
}

class _UserBubble extends StatelessWidget {
  const _UserBubble(this.m);
  final MeetingSummary m;
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final time = m.createdAt == null ? '' : '${m.createdAt!.hour.toString().padLeft(2, '0')}:${m.createdAt!.minute.toString().padLeft(2, '0')}';
    return Align(
      alignment: Alignment.centerRight,
      child: Container(
        margin: const EdgeInsets.only(left: 48, bottom: 6, top: 6),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(color: scheme.primary, borderRadius: BorderRadius.circular(16)),
        child: Text(
          m.kind == 'audio' ? '🎙 Meeting recording  $time' : m.input,
          style: TextStyle(color: scheme.onPrimary),
        ),
      ),
    );
  }
}

class _AssistantBubble extends StatelessWidget {
  const _AssistantBubble(this.m, {required this.onOpen, required this.onRetry, required this.onNotes, this.outcome});
  final MeetingSummary m;
  final String? outcome;
  final VoidCallback onOpen;
  final VoidCallback onRetry;
  final VoidCallback onNotes;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    Widget child;
    if (m.processing) {
      child = Row(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
        const SizedBox(width: 10),
        Text(switch (m.status) {
          'transcribing' => 'Listening to the recording…',
          'thinking' => 'Finding tasks and owners…',
          _ => 'Got it, working on it…',
        }),
      ]);
    } else if (m.status == 'error') {
      child = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('That one failed.', style: TextStyle(color: scheme.error, fontWeight: FontWeight.w600)),
        TextButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: const Text('Try again')),
      ]);
    } else {
      child = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (m.title.isNotEmpty) Text(m.title, style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Text(m.summary),
        if (m.tasks.isNotEmpty) ...[
          const SizedBox(height: 8),
          for (final t in m.tasks)
            Padding(
              padding: const EdgeInsets.only(bottom: 2),
              child: Text('☐ ${t.title} → ${t.owner}${t.due != null ? ' · ${t.due}' : ''}'),
            ),
        ],
        const SizedBox(height: 6),
        Wrap(spacing: 6, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
          if (m.notionUrl.isNotEmpty) const _Badge('Notion ✓'),
          if (outcome != null && outcome!.contains('Calendar')) _Badge(outcome!.split(' · ').first),
          ActionChip(avatar: const Icon(Icons.note_add_outlined, size: 16), label: const Text('Notes'), onPressed: onNotes, visualDensity: VisualDensity.compact),
          ActionChip(avatar: const Icon(Icons.open_in_full, size: 16), label: const Text('Details'), onPressed: onOpen, visualDensity: VisualDensity.compact),
        ]),
      ]);
    }
    return Align(
      alignment: Alignment.centerLeft,
      child: GestureDetector(
        onTap: m.processing ? null : onOpen,
        child: Container(
          margin: const EdgeInsets.only(right: 32, bottom: 10),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(color: scheme.surfaceContainerHigh, borderRadius: BorderRadius.circular(16)),
          child: child,
        ),
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge(this.label);
  final String label;
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(color: Colors.green.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(10)),
        child: Text(label, style: const TextStyle(fontSize: 12, color: Colors.green, fontWeight: FontWeight.w600)),
      );
}
