import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/share.dart';
import '../models/api_models.dart';
import '../widgets/empty_state.dart';
import '../widgets/error_state.dart';
import '../widgets/loading_state.dart';

/// Home tab: my to-dos from meetings, photos, voice memos and site visits.
class TasksScreen extends StatefulWidget {
  const TasksScreen({super.key});

  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen> {
  final api = ApiClient.instance;
  String _filter = 'open';
  late Future<List<TaskItem>> _future = _load();

  Future<List<TaskItem>> _load() async {
    final rows = await api.get('/me/tasks') as List;
    return rows.map((e) => TaskItem.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<void> _refresh() async {
    setState(() => _future = _load());
    await _future;
  }

  Future<void> _setStatus(TaskItem t, String status) async {
    final old = t.status;
    setState(() => t.status = status);
    try {
      await api.send('PATCH', '/tasks/${t.id}', {'status': status});
    } on ApiException catch (e) {
      setState(() => t.status = old);
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Hi ${api.userName}', style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
            Text('Your tasks', style: text.labelSmall),
          ],
        ),
      ),
      body: FutureBuilder<List<TaskItem>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const LoadingState(message: 'Loading tasks...');
          if (snap.hasError) return ErrorState(message: snap.error.toString(), onRetry: _refresh);
          final all = snap.data!;
          final shown = switch (_filter) {
            'open' => all.where((t) => !t.done).toList(),
            'done' => all.where((t) => t.done).toList(),
            _ => all,
          };
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
              children: [
                Wrap(spacing: 8, children: [
                  for (final f in const ['open', 'done', 'all'])
                    ChoiceChip(
                      label: Text('${f[0].toUpperCase()}${f.substring(1)}'),
                      selected: _filter == f,
                      onSelected: (_) => setState(() => _filter = f),
                    ),
                ]),
                const SizedBox(height: 8),
                if (shown.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 60),
                    child: EmptyState(
                      icon: Icons.task_alt,
                      title: 'Nothing here',
                      subtitle: 'Tasks appear after a meeting, a photo of a board, or a voice memo.',
                    ),
                  ),
                for (final t in shown) _TaskTile(task: t, onStatus: (s) => _setStatus(t, s)),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _TaskTile extends StatelessWidget {
  const _TaskTile({required this.task, required this.onStatus});

  final TaskItem task;
  final ValueChanged<String> onStatus;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = switch (task.priority) {
      'high' => scheme.error,
      'medium' => Colors.orange,
      _ => scheme.outline,
    };
    return Card(
      child: ListTile(
        leading: Checkbox(value: task.done, onChanged: (v) => onStatus(v == true ? 'done' : 'todo')),
        title: Text(task.title,
            style: task.done ? const TextStyle(decoration: TextDecoration.lineThrough) : null),
        subtitle: Row(children: [
          Icon(Icons.flag, size: 14, color: color),
          const SizedBox(width: 4),
          Text(task.priority),
          if (task.due != null) ...[const SizedBox(width: 12), const Icon(Icons.event, size: 14), const SizedBox(width: 4), Text(task.due!)],
        ]),
        trailing: task.notionUrl.isEmpty
            ? null
            : IconButton(tooltip: 'Open in Notion', icon: const Icon(Icons.open_in_new), onPressed: () => openLink(task.notionUrl)),
      ),
    );
  }
}
