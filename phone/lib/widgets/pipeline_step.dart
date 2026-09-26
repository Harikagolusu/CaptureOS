import 'package:flutter/material.dart';

class PipelineRow extends StatelessWidget {
  const PipelineRow({super.key});

  static const _steps = [
    ('Capture', Icons.photo_camera_outlined),
    ('Understand', Icons.psychology_outlined),
    ('Review', Icons.fact_check_outlined),
    ('Evidence', Icons.picture_as_pdf_outlined),
  ];

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      label: 'Capture workflow: Capture, Understand, Review, Evidence',
      child: Row(
        children: [
          for (int i = 0; i < _steps.length; i++) ...[
            Expanded(child: _StepChip(label: _steps[i].$1, icon: _steps[i].$2)),
            if (i < _steps.length - 1)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Icon(Icons.arrow_forward, size: 14, color: scheme.outline),
              ),
          ],
        ],
      ),
    );
  }
}

class _StepChip extends StatelessWidget {
  const _StepChip({required this.label, required this.icon});

  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
      decoration: BoxDecoration(
        color: scheme.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: scheme.outlineVariant),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 20, color: scheme.primary),
          const SizedBox(height: 6),
          Text(
            label,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                  color: scheme.onSurface,
                ),
            textAlign: TextAlign.center,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
