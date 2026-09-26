import 'package:flutter/material.dart';
import '../models/evidence.dart';

class EvidenceCard extends StatelessWidget {
  const EvidenceCard({super.key, required this.evidence});

  final Evidence evidence;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final isPhoto = evidence.type == 'photo';
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: scheme.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: scheme.outlineVariant),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: isPhoto ? scheme.primaryContainer : scheme.secondaryContainer,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(isPhoto ? Icons.photo : Icons.mic, color: isPhoto ? scheme.onPrimaryContainer : scheme.onSecondaryContainer, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(evidence.displayName, style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                Text('${evidence.type} • ${evidence.timestamp.month}/${evidence.timestamp.day}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant, fontSize: 12)),
                if (evidence.location != null)
                  Text(evidence.location!, style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.outline, fontSize: 11)),
              ],
            ),
          ),
          Icon(Icons.check_circle, size: 16, color: scheme.primary),
        ],
      ),
    );
  }
}
