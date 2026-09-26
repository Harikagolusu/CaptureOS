import 'package:flutter/material.dart';
import '../models/observation.dart';
import '../widgets/evidence_card.dart';
import '../models/evidence.dart';

class EvidenceScreen extends StatelessWidget {
  const EvidenceScreen({super.key, required this.draft});

  final ObservationDraft draft;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final evidence = <Evidence>[
      if (draft.hasPhoto)
        Evidence(id: 'p1', observationId: draft.id, type: 'photo', path: draft.photoPath!, timestamp: draft.capturedAt, location: draft.location),
      if (draft.hasAudio)
        Evidence(id: 'v1', observationId: draft.id, type: 'voice', path: draft.audioPath!, timestamp: draft.capturedAt, location: draft.location),
      ...draft.evidence,
    ];
    return Scaffold(
      appBar: AppBar(title: const Text('Evidence')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Evidence', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 8),
                    _MetaRow(label: 'Timestamp', value: _formatDate(draft.capturedAt)),
                    _MetaRow(label: 'Location', value: draft.location ?? '—'),
                    _MetaRow(label: 'Items', value: '${evidence.length}'),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (evidence.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(color: scheme.surfaceContainer, borderRadius: BorderRadius.circular(12)),
                  child: Column(
                    children: [
                      Icon(Icons.folder_open, size: 32, color: scheme.outline),
                      const SizedBox(height: 8),
                      Text('No evidence yet', style: Theme.of(context).textTheme.bodyMedium),
                      Text('Add a photo or voice note', style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
                    ],
                  ),
                )
              else
                Column(
                  children: evidence.map((e) => Padding(padding: const EdgeInsets.only(bottom: 10), child: EvidenceCard(evidence: e))).toList(),
                ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () => ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Add Evidence — coming soon (mock)'))),
                  icon: const Icon(Icons.add),
                  label: const Text('Add Evidence'),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Done'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime d) => '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')} ${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
}

class _MetaRow extends StatelessWidget {
  const _MetaRow({required this.label, required this.value});
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          SizedBox(width: 80, child: Text(label, style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.outline))),
          const SizedBox(width: 8),
          Expanded(child: Text(value, style: Theme.of(context).textTheme.bodySmall)),
        ],
      ),
    );
  }
}
