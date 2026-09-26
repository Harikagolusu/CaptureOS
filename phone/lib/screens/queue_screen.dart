import 'package:flutter/material.dart';
import '../services/mock_observation_service.dart';
import '../widgets/observation_card.dart';

class QueueScreen extends StatefulWidget {
  const QueueScreen({super.key});

  @override
  State<QueueScreen> createState() => _QueueScreenState();
}

class _QueueScreenState extends State<QueueScreen> {
  final _service = MockObservationService();
  late Future<List<dynamic>> _future;

  @override
  void initState() {
    super.initState();
    _future = _service.getQueue();
  }

  Future<void> _refresh() async {
    setState(() => _future = _service.getQueue());
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Offline Queue')),
      body: SafeArea(
        child: FutureBuilder<List<dynamic>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            final items = snap.data ?? [];
            return RefreshIndicator(
              onRefresh: _refresh,
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: scheme.secondaryContainer,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.cloud_off, color: scheme.onSecondaryContainer),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('${items.length} items waiting',
                                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                        color: scheme.onSecondaryContainer, fontWeight: FontWeight.w600)),
                                Text('Offline • No sync required yet (mock)',
                                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSecondaryContainer)),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    if (items.isEmpty)
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(20),
                        decoration: BoxDecoration(color: scheme.surfaceContainer, borderRadius: BorderRadius.circular(12)),
                        child: Column(
                          children: [
                            Icon(Icons.inbox_outlined, size: 32, color: scheme.outline),
                            const SizedBox(height: 8),
                            Text('Queue empty', style: Theme.of(context).textTheme.bodyMedium),
                          ],
                        ),
                      )
                    else
                      Column(
                        children: items
                            .map((o) => Padding(
                                  padding: const EdgeInsets.only(bottom: 10),
                                  child: Stack(
                                    children: [
                                      ObservationCard(observation: o),
                                      Positioned(
                                        right: 8,
                                        top: 8,
                                        child: Row(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            _MiniButton(label: 'View', onTap: () {}),
                                            const SizedBox(width: 6),
                                            _MiniButton(label: 'Retry', onTap: () => ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Retry — mock, no sync')))),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ))
                            .toList(),
                      ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _MiniButton extends StatelessWidget {
  const _MiniButton({required this.label, required this.onTap});
  final String label;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 28,
      child: OutlinedButton(
        onPressed: onTap,
        style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 10), minimumSize: const Size(0, 28)),
        child: Text(label, style: const TextStyle(fontSize: 12)),
      ),
    );
  }
}
