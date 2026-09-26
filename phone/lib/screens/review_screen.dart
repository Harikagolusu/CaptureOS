import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import '../models/observation.dart';
import 'confirmation_screen.dart';
import 'evidence_screen.dart';

class ReviewScreen extends StatefulWidget {
  const ReviewScreen({super.key, required this.draft});

  final ObservationDraft draft;

  @override
  State<ReviewScreen> createState() => _ReviewScreenState();
}

class _ReviewScreenState extends State<ReviewScreen> {
  late ObservationDraft _draft;
  bool _approving = false;

  @override
  void initState() {
    super.initState();
    _draft = widget.draft;
  }

  void _approve() async {
    setState(() => _approving = true);
    await Future<void>.delayed(const Duration(milliseconds: 400));
    if (!mounted) return;
    setState(() {
      _draft = _draft.approved();
      _approving = false;
    });
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Observation approved — queued locally (offline).'),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Review'),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (_draft.isApproved)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  decoration: BoxDecoration(
                    color: scheme.primaryContainer,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.verified, size: 18, color: scheme.onPrimaryContainer),
                      const SizedBox(width: 8),
                      Text(
                        'Approved — human approval recorded',
                        style: text.labelMedium?.copyWith(
                          color: scheme.onPrimaryContainer,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              if (_draft.isApproved) const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(14),
                child: AspectRatio(
                  aspectRatio: 4 / 3,
                  child: _draft.hasPhoto
                      ? Container(
                          color: scheme.primaryContainer,
                          child: Center(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.photo, size: 32, color: scheme.onPrimaryContainer),
                                const SizedBox(height: 8),
                                Text('Mock photo', style: text.bodySmall?.copyWith(color: scheme.onPrimaryContainer)),
                                Text(p.basename(_draft.photoPath!), style: text.labelSmall?.copyWith(color: scheme.onPrimaryContainer)),
                              ],
                            ),
                          ),
                        )
                      : Container(
                          color: scheme.surfaceContainerHighest,
                          child: Center(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.image_not_supported_outlined,
                                    size: 32, color: scheme.outline),
                                const SizedBox(height: 8),
                                Text('No photo captured', style: text.bodySmall),
                              ],
                            ),
                          ),
                        ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Chip(
                    avatar: Icon(
                      _draft.hasPhoto ? Icons.check_circle : Icons.cancel_outlined,
                      size: 16,
                      color: _draft.hasPhoto ? scheme.primary : scheme.outline,
                    ),
                    label: Text(_draft.hasPhoto ? 'Photo attached' : 'No photo'),
                  ),
                  const SizedBox(width: 8),
                  Chip(
                    avatar: Icon(
                      _draft.hasAudio ? Icons.check_circle : Icons.cancel_outlined,
                      size: 16,
                      color: _draft.hasAudio ? scheme.primary : scheme.outline,
                    ),
                    label: Text(_draft.hasAudio ? 'Voice note' : 'No voice'),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.schedule, size: 16, color: scheme.primary),
                          const SizedBox(width: 6),
                          Text(
                            'Captured ${_formatDate(_draft.capturedAt)}',
                            style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text('ID ${_draft.id}', style: text.labelSmall?.copyWith(color: scheme.outline)),
                      const SizedBox(height: 8),
                      Text(
                        _draft.title ?? 'Untitled observation',
                        style: text.titleMedium?.copyWith(fontWeight: FontWeight.w600),
                      ),
                      if (_draft.description != null) ...[
                        const SizedBox(height: 6),
                        Text(_draft.description!, style: text.bodyMedium),
                      ],
                      if (_draft.location != null) ...[
                        const SizedBox(height: 6),
                        Row(
                          children: [
                            Icon(Icons.place, size: 14, color: scheme.outline),
                            const SizedBox(width: 4),
                            Text(_draft.location!, style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
                          ],
                        ),
                      ],
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        children: [
                          Chip(label: Text(_draft.category.label), visualDensity: VisualDensity.compact),
                          Chip(label: Text(_draft.priority.label), visualDensity: VisualDensity.compact),
                        ],
                      ),
                      if (_draft.notes != null) ...[
                        const SizedBox(height: 6),
                        Text(_draft.notes!, style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
                      ],
                      const SizedBox(height: 12),
                      Divider(color: scheme.outlineVariant),
                      const SizedBox(height: 8),
                      _MetaRow(label: 'Status', value: _draft.status.label),
                      _MetaRow(label: 'Photo', value: _draft.photoPath != null ? p.basename(_draft.photoPath!) : '—'),
                      _MetaRow(label: 'Audio', value: _draft.audioPath != null ? p.basename(_draft.audioPath!) : '—'),
                      const SizedBox(height: 10),
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: scheme.surfaceContainer,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.link, size: 16, color: scheme.primary),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Evidence links retained locally (photo + audio).',
                                style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.edit_outlined, size: 18),
                      label: const Text('Retake / Edit'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        Navigator.of(context).popUntil((r) => r.isFirst);
                      },
                      icon: const Icon(Icons.home_outlined, size: 18),
                      label: const Text('Home'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: _draft.isApproved || _approving ? null : _approve,
                  icon: _approving
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.verified_outlined),
                  label: Text(_draft.isApproved ? 'Approved' : 'Approve Observation'),
                ),
              ),
              if (_draft.isApproved) ...[
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => EvidenceScreen(draft: _draft))),
                        icon: const Icon(Icons.folder_outlined, size: 18),
                        label: const Text('Evidence'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ConfirmationScreen(draft: _draft))),
                        icon: const Icon(Icons.check, size: 18),
                        label: const Text('Confirmation'),
                      ),
                    ),
                  ],
                ),
              ],
              const SizedBox(height: 8),
              Center(
                child: Text(
                  'Human approval is mandatory before export.',
                  style: text.labelSmall?.copyWith(color: scheme.outline),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime d) {
    return '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')} '
        '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
  }
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
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 70,
            child: Text(
              label,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.outline),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              value,
              style: Theme.of(context).textTheme.bodySmall,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
