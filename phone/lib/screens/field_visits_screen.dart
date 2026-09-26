import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../widgets/empty_state.dart';
import '../widgets/error_state.dart';
import '../widgets/loading_state.dart';
import 'field_visit_screen.dart';

/// Field Mode: site visits. Each visit collects photo + voice observations and ends in a report.
class FieldVisitsScreen extends StatefulWidget {
  const FieldVisitsScreen({super.key});

  @override
  State<FieldVisitsScreen> createState() => _FieldVisitsScreenState();
}

class _FieldVisitsScreenState extends State<FieldVisitsScreen> {
  final api = ApiClient.instance;
  late Future<List<Map<String, dynamic>>> _future = _load();

  Future<List<Map<String, dynamic>>> _load() async =>
      (await api.get('/field/visits') as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();

  Future<void> _refresh() async {
    setState(() => _future = _load());
    await _future;
  }

  Future<void> _open(int id) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => FieldVisitScreen(visitId: id)));
    _refresh();
  }

  Future<void> _newVisit() async {
    final c = TextEditingController();
    final site = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New site visit'),
        content: TextField(
          controller: c,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Site', hintText: 'Block B handover', border: OutlineInputBorder()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, c.text.trim()), child: const Text('Start')),
        ],
      ),
    );
    if (site == null || site.isEmpty) return;
    try {
      final v = await api.send('POST', '/field/visits', {'site': site}) as Map;
      _open(v['id'] as int);
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Field Mode')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _newVisit,
        icon: const Icon(Icons.add_location_alt_outlined),
        label: const Text('New visit'),
      ),
      body: FutureBuilder<List<Map<String, dynamic>>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const LoadingState(message: 'Loading visits...');
          if (snap.hasError) return ErrorState(message: snap.error.toString(), onRetry: _refresh);
          final rows = snap.data!;
          if (rows.isEmpty) {
            return const EmptyState(
              icon: Icons.engineering_outlined,
              title: 'No site visits',
              subtitle: 'Start a visit, snap problems with a voice note, get a report with photos.',
            );
          }
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 96),
              children: [
                for (final v in rows)
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.place_outlined),
                      title: Text(v['site'] as String),
                      subtitle: Text(v['report_status'] == 'done' ? 'Report ready' : 'In progress'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => _open(v['id'] as int),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}
