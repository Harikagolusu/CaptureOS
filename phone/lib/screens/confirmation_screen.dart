import 'package:flutter/material.dart';
import '../models/observation.dart';

class ConfirmationScreen extends StatelessWidget {
  const ConfirmationScreen({super.key, required this.draft});

  final ObservationDraft draft;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 40, 24, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(color: Colors.green.shade100, shape: BoxShape.circle),
                child: Icon(Icons.check, size: 42, color: Colors.green.shade700),
              ),
              const SizedBox(height: 16),
              Text('Observation Approved', style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text('✓ Observation saved locally (offline).', style: text.bodyMedium?.copyWith(color: scheme.onSurfaceVariant)),
              const SizedBox(height: 24),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Evidence', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 12),
                      _RowIcon(icon: Icons.photo, label: draft.hasPhoto ? 'Photo attached' : 'No photo', ok: draft.hasPhoto),
                      const SizedBox(height: 8),
                      _RowIcon(icon: Icons.mic, label: draft.hasAudio ? 'Voice note attached' : 'No voice note', ok: draft.hasAudio),
                      const SizedBox(height: 8),
                      _RowIcon(icon: Icons.schedule, label: _formatDate(draft.capturedAt), ok: true),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => Navigator.of(context).popUntil((r) => r.isFirst),
                  child: const Text('Back to Home'),
                ),
              ),
              const SizedBox(height: 8),
              Text('Evidence retained: ${draft.evidenceCount} • Offline queue pending sync is mock', style: text.labelSmall?.copyWith(color: scheme.outline), textAlign: TextAlign.center),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime d) => '${d.year}/${d.month}/${d.day} ${d.hour}:${d.minute.toString().padLeft(2, '0')}';

}

class _RowIcon extends StatelessWidget {
  const _RowIcon({required this.icon, required this.label, required this.ok});
  final IconData icon;
  final String label;
  final bool ok;
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Row(
      children: [
        Icon(icon, size: 18, color: ok ? scheme.primary : scheme.outline),
        const SizedBox(width: 8),
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
        const Spacer(),
        Icon(ok ? Icons.check_circle : Icons.cancel, size: 16, color: ok ? Colors.green : scheme.outline),
      ],
    );
  }
}
