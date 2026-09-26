import 'package:flutter/material.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  ThemeMode _mode = ThemeMode.system;
  bool _notifications = true;
  bool _offlineMode = true;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
          children: [
            Text('Appearance', style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            SegmentedButton<ThemeMode>(
              segments: const [
                ButtonSegment(value: ThemeMode.system, label: Text('System'), icon: Icon(Icons.brightness_auto, size: 16)),
                ButtonSegment(value: ThemeMode.light, label: Text('Light'), icon: Icon(Icons.light_mode, size: 16)),
                ButtonSegment(value: ThemeMode.dark, label: Text('Dark'), icon: Icon(Icons.dark_mode, size: 16)),
              ],
              selected: {_mode},
              onSelectionChanged: (s) => setState(() => _mode = s.first),
            ),
            const SizedBox(height: 20),
            SwitchListTile(
              title: const Text('Notifications'),
              subtitle: const Text('Field reminders (mock)'),
              value: _notifications,
              onChanged: (v) => setState(() => _notifications = v),
            ),
            SwitchListTile(
              title: const Text('Offline Mode'),
              subtitle: const Text('Keep data on device only'),
              value: _offlineMode,
              onChanged: (v) => setState(() => _offlineMode = v),
            ),
            const Divider(height: 32),
            ListTile(
              leading: const Icon(Icons.info_outline),
              title: const Text('About'),
              subtitle: const Text('CaptureOS 0.1.0 • Offline-first field capture'),
              onTap: () => showAboutDialog(
                context: context,
                applicationName: 'CaptureOS',
                applicationVersion: '0.1.0',
                children: [const Text('Frontend-only demo. Backend to be connected by teammate.')],
              ),
            ),
            ListTile(
              leading: const Icon(Icons.memory_outlined),
              title: const Text('On-device, hardware-accelerated'),
              subtitle: const Text('No NPU claim • Mock AI pending backend'),
            ),
          ],
        ),
      ),
    );
  }
}
