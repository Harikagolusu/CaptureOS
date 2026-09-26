import 'package:flutter/material.dart';
import '../models/observation.dart';

class ObservationDetailsScreen extends StatefulWidget {
  const ObservationDetailsScreen({super.key, required this.draft});

  final ObservationDraft draft;

  @override
  State<ObservationDetailsScreen> createState() => _ObservationDetailsScreenState();
}

class _ObservationDetailsScreenState extends State<ObservationDetailsScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _title;
  late final TextEditingController _description;
  late final TextEditingController _location;
  late final TextEditingController _notes;
  ObservationCategory _category = ObservationCategory.other;
  ObservationPriority _priority = ObservationPriority.medium;

  @override
  void initState() {
    super.initState();
    _title = TextEditingController(text: widget.draft.title ?? '');
    _description = TextEditingController(text: widget.draft.description ?? '');
    _location = TextEditingController(text: widget.draft.location ?? '');
    _notes = TextEditingController(text: widget.draft.notes ?? '');
    _category = widget.draft.category;
    _priority = widget.draft.priority;
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _location.dispose();
    _notes.dispose();
    super.dispose();
  }

  void _continue() {
    if (!_formKey.currentState!.validate()) return;
    final updated = widget.draft.copyWith(
      title: _title.text.trim(),
      description: _description.text.trim(),
      location: _location.text.trim(),
      category: _category,
      priority: _priority,
      notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      status: ObservationStatus.needsReview,
    );
    Navigator.of(context).pop(updated);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Observation Details'), leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => Navigator.of(context).pop())),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Structured observation', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 4),
                Text('Add details for human review. Fields marked * are required.', style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _title,
                  decoration: const InputDecoration(labelText: 'Observation Title *', hintText: 'e.g. Scaffold check - Bay 3', border: OutlineInputBorder()),
                  textInputAction: TextInputAction.next,
                  validator: (v) => v == null || v.trim().isEmpty ? 'Title is required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _description,
                  decoration: const InputDecoration(labelText: 'Description *', hintText: 'What did you observe?', border: OutlineInputBorder()),
                  maxLines: 3,
                  validator: (v) => v == null || v.trim().isEmpty ? 'Description is required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _location,
                  decoration: const InputDecoration(labelText: 'Location *', hintText: 'Site A - Bay 3', border: OutlineInputBorder(), prefixIcon: Icon(Icons.place_outlined, size: 18)),
                  validator: (v) => v == null || v.trim().isEmpty ? 'Location is required' : null,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<ObservationCategory>(
                        initialValue: _category,
                        decoration: const InputDecoration(labelText: 'Category *', border: OutlineInputBorder()),
                        items: ObservationCategory.values
                            .map((c) => DropdownMenuItem(value: c, child: Text(c.label, style: const TextStyle(fontSize: 14))))
                            .toList(),
                        onChanged: (v) => setState(() => _category = v ?? ObservationCategory.other),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: DropdownButtonFormField<ObservationPriority>(
                        initialValue: _priority,
                        decoration: const InputDecoration(labelText: 'Priority *', border: OutlineInputBorder()),
                        items: ObservationPriority.values
                            .map((p) => DropdownMenuItem(
                                  value: p,
                                  child: Row(
                                    children: [
                                      Icon(
                                        p == ObservationPriority.critical
                                            ? Icons.priority_high
                                            : p == ObservationPriority.high
                                                ? Icons.arrow_upward
                                                : Icons.remove,
                                        size: 14,
                                        color: p == ObservationPriority.critical ? Colors.red : scheme.primary,
                                      ),
                                      const SizedBox(width: 6),
                                      Text(p.label, style: const TextStyle(fontSize: 14)),
                                    ],
                                  ),
                                ))
                            .toList(),
                        onChanged: (v) => setState(() => _priority = v ?? ObservationPriority.medium),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _notes,
                  decoration: const InputDecoration(labelText: 'Notes', hintText: 'Additional context (optional)', border: OutlineInputBorder()),
                  maxLines: 4,
                ),
                const SizedBox(height: 20),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: _continue,
                    icon: const Icon(Icons.arrow_forward),
                    label: const Text('Continue to Review'),
                  ),
                ),
                const SizedBox(height: 8),
                Center(child: Text('Data saved locally — offline only', style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.outline))),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
