import 'package:flutter/material.dart';
import '../models/observation.dart';
import 'status_badge.dart';

class ObservationCard extends StatelessWidget {
  const ObservationCard({super.key, required this.observation, this.onTap});

  final ObservationDraft observation;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  color: observation.hasPhoto ? scheme.primaryContainer : scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  observation.hasPhoto ? Icons.photo : Icons.note_outlined,
                  color: observation.hasPhoto ? scheme.onPrimaryContainer : scheme.outline,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            observation.title ?? 'Untitled',
                            style: text.titleSmall?.copyWith(fontWeight: FontWeight.w600),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        StatusBadge(status: observation.status),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      observation.description ?? observation.notes ?? 'No description',
                      style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Icon(Icons.schedule, size: 12, color: scheme.outline),
                        const SizedBox(width: 4),
                        Text(_formatDate(observation.capturedAt), style: text.labelSmall?.copyWith(color: scheme.outline, fontSize: 11)),
                        const SizedBox(width: 12),
                        Icon(Icons.link, size: 12, color: scheme.outline),
                        const SizedBox(width: 4),
                        Text('${observation.evidenceCount} evidence', style: text.labelSmall?.copyWith(color: scheme.outline, fontSize: 11)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime d) => '${d.month}/${d.day} ${d.hour}:${d.minute.toString().padLeft(2, '0')}';
}
