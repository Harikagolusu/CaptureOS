import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../models/api_models.dart';
import '../widgets/empty_state.dart';
import '../widgets/error_state.dart';
import '../widgets/loading_state.dart';
import 'meeting_detail_screen.dart';
import 'record_meeting_screen.dart';

class MeetingsScreen extends StatefulWidget {
  const MeetingsScreen({super.key});

  @override
  State<MeetingsScreen> createState() => _MeetingsScreenState();
}

class _MeetingsScreenState extends State<MeetingsScreen> {
  final api = ApiClient.instance;
  late Future<List<MeetingSummary>> _future = _load();

  Future<List<MeetingSummary>> _load() async {
    final rows = await api.get('/meetings') as List;
    return rows.map((e) => MeetingSummary.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<void> _refresh() async {
    setState(() => _future = _load());
    await _future;
  }

  Future<void> _open(int id) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => MeetingDetailScreen(meetingId: id)));
    _refresh();
  }

  Future<void> _record() async {
    final id = await Navigator.of(context).push<int>(MaterialPageRoute(builder: (_) => const RecordMeetingScreen()));
    if (id != null) _open(id);
  }

  /// Upload an existing recording (phone recorder app, WhatsApp audio, a video…).
  Future<void> _uploadFile() async {
    final picked = await FilePicker.pickFiles(type: FileType.any);
    final path = picked.firstOrNull?.path;
    if (path == null) return;
    try {
      final r = await api.upload('/meetings', {'audio': [path]}) as Map;
      _open(r['id'] as int);
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Meetings'),
        actions: [IconButton(tooltip: 'Upload a recording', icon: const Icon(Icons.upload_file), onPressed: _uploadFile)],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _record,
        icon: const Icon(Icons.mic),
        label: const Text('Record meeting'),
      ),
      body: FutureBuilder<List<MeetingSummary>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const LoadingState(message: 'Loading meetings...');
          if (snap.hasError) return ErrorState(message: snap.error.toString(), onRetry: _refresh);
          final rows = snap.data!;
          if (rows.isEmpty) {
            return const EmptyState(
              icon: Icons.groups_outlined,
              title: 'No meetings yet',
              subtitle: 'Record one, and everyone gets their tasks automatically.',
            );
          }
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView.builder(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 96),
              itemCount: rows.length,
              itemBuilder: (_, i) {
                final m = rows[i];
                return Card(
                  child: ListTile(
                    leading: m.processing
                        ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
                        : Icon(m.status == 'error' ? Icons.error_outline : Icons.event_note),
                    title: Text(m.title.isEmpty ? 'Meeting #${m.id}' : m.title),
                    subtitle: Text(
                      m.processing ? 'Processing (${m.status})…' : (m.summary.isEmpty ? m.status : m.summary),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    onTap: () => _open(m.id),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
