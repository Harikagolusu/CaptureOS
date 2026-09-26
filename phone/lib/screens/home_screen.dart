import 'package:flutter/material.dart';
import '../core/constants.dart';
import '../models/observation.dart';
import '../services/mock_observation_service.dart';
import '../widgets/observation_card.dart';
import '../widgets/capture_button.dart';
import 'capture_screen.dart';
import 'queue_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _service = MockObservationService();
  late Future<List<ObservationDraft>> _future;

  @override
  void initState() {
    super.initState();
    _future = _service.getObservations();
  }

  void _onCapture() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const CaptureScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(AppConstants.appName, style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
            Text('Field Mode', style: text.labelSmall?.copyWith(color: scheme.primary, fontWeight: FontWeight.w600, letterSpacing: 0.6)),
          ],
        ),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 12),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: scheme.primaryContainer,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(width: 8, height: 8, decoration: BoxDecoration(color: Colors.green, shape: BoxShape.circle)),
                const SizedBox(width: 6),
                Text('Offline Ready', style: text.labelSmall?.copyWith(color: scheme.onPrimaryContainer, fontWeight: FontWeight.w600, fontSize: 11)),
              ],
            ),
          ),
          IconButton(icon: const Icon(Icons.info_outline), tooltip: 'About', onPressed: () => _showAbout(context)),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async => setState(() => _future = _service.getObservations()),
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CaptureButton(onPressed: _onCapture),
                const SizedBox(height: 16),
                Row(
                  children: [
                    _QuickAction(
                      icon: Icons.history,
                      label: 'Recent Captures',
                      onTap: () {},
                    ),
                    const SizedBox(width: 10),
                    _QuickAction(
                      icon: Icons.cloud_queue_outlined,
                      label: 'Offline Queue',
                      badge: '3',
                      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const QueueScreen())),
                    ),
                    const SizedBox(width: 10),
                    _QuickAction(
                      icon: Icons.settings_outlined,
                      label: 'Settings',
                      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettingsScreen())),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Recent observations', style: text.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                    TextButton(onPressed: () {}, child: const Text('View all')),
                  ],
                ),
                const SizedBox(height: 8),
                FutureBuilder<List<ObservationDraft>>(
                  future: _future,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()));
                    }
                    if (snapshot.hasError) {
                      return Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Failed to load: ${snapshot.error}')));
                    }
                    final items = snapshot.data ?? [];
                    if (items.isEmpty) {
                      return Card(
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Center(child: Text('No captures yet', style: text.bodyMedium)),
                        ),
                      );
                    }
                    return Column(
                      children: items
                          .map((o) => Padding(
                                padding: const EdgeInsets.only(bottom: 10),
                                child: ObservationCard(
                                  observation: o,
                                  onTap: () => ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Open ${o.title}')),
                                  ),
                                ),
                              ))
                          .toList(),
                    );
                  },
                ),
                const SizedBox(height: 20),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: scheme.surfaceContainer,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: scheme.outlineVariant),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Capture → Understand → Review → Evidence',
                          style: text.labelMedium?.copyWith(color: scheme.primary, fontWeight: FontWeight.w700)),
                      const SizedBox(height: 6),
                      Text('Offline-first, human-in-the-loop, evidence-linked. Phone PDF mandatory.',
                          style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                Center(child: Text('v0.1.0 • iQOO / Android • Mock data', style: text.labelSmall?.copyWith(color: scheme.outline))),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showAbout(BuildContext context) {
    showAboutDialog(
      context: context,
      applicationName: 'CaptureOS',
      applicationVersion: '0.1.0',
      applicationLegalese: '30-hour hackathon — offline field capture.',
      children: [const SizedBox(height: 12), Text('Offline Ready • Field Mode hero • on-device, hardware-accelerated', style: Theme.of(context).textTheme.bodySmall)],
    );
  }
}

class _QuickAction extends StatelessWidget {
  const _QuickAction({required this.icon, required this.label, this.badge, required this.onTap});
  final IconData icon;
  final String label;
  final String? badge;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.6)),
          ),
          child: Column(
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  Icon(icon, size: 20, color: scheme.primary),
                  if (badge != null)
                    Positioned(
                      right: -8,
                      top: -6,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                        decoration: BoxDecoration(color: scheme.primary, shape: BoxShape.circle),
                        child: Text(badge!, style: TextStyle(color: scheme.onPrimary, fontSize: 10, fontWeight: FontWeight.w700)),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 6),
              Text(label, style: Theme.of(context).textTheme.labelSmall?.copyWith(fontWeight: FontWeight.w600, fontSize: 11), textAlign: TextAlign.center),
            ],
          ),
        ),
      ),
    );
  }
}
