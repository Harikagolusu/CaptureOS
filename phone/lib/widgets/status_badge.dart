import 'package:flutter/material.dart';
import '../models/observation.dart';

class StatusBadge extends StatelessWidget {
  const StatusBadge({super.key, required this.status});

  final ObservationStatus status;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final (bg, fg) = switch (status) {
      ObservationStatus.draft => (scheme.surfaceContainerHighest, scheme.onSurfaceVariant),
      ObservationStatus.needsReview => (scheme.tertiaryContainer, scheme.onTertiaryContainer),
      ObservationStatus.approved => (scheme.primaryContainer, scheme.onPrimaryContainer),
      ObservationStatus.queued => (scheme.secondaryContainer, scheme.onSecondaryContainer),
      ObservationStatus.exported => (scheme.surfaceContainer, scheme.onSurfaceVariant),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(20)),
      child: Text(
        status.label,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: fg,
              fontWeight: FontWeight.w700,
              fontSize: 11,
              letterSpacing: 0.3,
            ),
      ),
    );
  }
}
