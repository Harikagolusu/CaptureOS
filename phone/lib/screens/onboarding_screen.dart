import 'package:flutter/material.dart';

import '../core/api_client.dart';

const profiles = {
  'employee': 'Employee / office',
  'student': 'Student',
  'manager': 'Manager / team lead',
  'field': 'Field worker',
};

/// First launch: server address, then create a team (admin) or join one with a code (member).
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key, required this.onDone});

  final VoidCallback onDone;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final api = ApiClient.instance;
  late final _server = TextEditingController(text: api.baseUrl);
  final _name = TextEditingController();
  final _nicknames = TextEditingController();
  final _team = TextEditingController();
  final _code = TextEditingController();
  bool _joining = true;
  String _profile = 'employee';
  bool _busy = false;
  String? _error;
  bool? _serverOk;

  Future<void> _checkServer() async {
    await api.setBaseUrl(_server.text);
    final ok = await api.health();
    if (mounted) setState(() => _serverOk = ok);
  }

  Future<void> _submit() async {
    if (_name.text.trim().isEmpty || (_joining ? _code.text.trim().isEmpty : _team.text.trim().isEmpty)) {
      setState(() => _error = _joining ? 'Enter the team code and your name' : 'Enter a team name and your name');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await api.setBaseUrl(_server.text);
      if (_joining) {
        await api.join(_code.text.trim(), _name.text.trim(), _profile);
      } else {
        final aliases = _nicknames.text.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
        await api.createTeam(_team.text.trim(), _name.text.trim(), _profile, aliases);
      }
      widget.onDone();
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 32, 20, 24),
          children: [
            Text('CaptureOS', style: text.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
            Text('Your meetings, photos and notes, turned into work.',
                style: text.bodyMedium?.copyWith(color: scheme.onSurfaceVariant)),
            const SizedBox(height: 28),
            TextField(
              controller: _server,
              keyboardType: TextInputType.url,
              decoration: InputDecoration(
                labelText: 'Server address',
                hintText: 'http://10.2.36.118:8000',
                border: const OutlineInputBorder(),
                suffixIcon: IconButton(
                  tooltip: 'Check',
                  onPressed: _checkServer,
                  icon: Icon(
                    _serverOk == null ? Icons.wifi_find : (_serverOk! ? Icons.check_circle : Icons.error_outline),
                    color: _serverOk == null ? null : (_serverOk! ? Colors.green : scheme.error),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 20),
            SegmentedButton<bool>(
              segments: const [
                ButtonSegment(value: true, label: Text('Join a team'), icon: Icon(Icons.group_add_outlined)),
                ButtonSegment(value: false, label: Text('Create team'), icon: Icon(Icons.admin_panel_settings_outlined)),
              ],
              selected: {_joining},
              onSelectionChanged: (s) => setState(() {
                _joining = s.first;
                _profile = _joining ? 'employee' : 'manager';
              }),
            ),
            const SizedBox(height: 16),
            if (_joining)
              TextField(
                controller: _code,
                textCapitalization: TextCapitalization.characters,
                decoration: const InputDecoration(labelText: 'Team code', hintText: '0KVE4Y', border: OutlineInputBorder()),
              )
            else
              TextField(
                controller: _team,
                decoration: const InputDecoration(labelText: 'Team name', border: OutlineInputBorder()),
              ),
            const SizedBox(height: 12),
            TextField(
              controller: _name,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(labelText: 'Your name', border: OutlineInputBorder()),
            ),
            if (!_joining) ...[
              const SizedBox(height: 12),
              TextField(
                controller: _nicknames,
                decoration: const InputDecoration(
                    labelText: 'Nicknames (optional)', hintText: 'Tej, Teja', border: OutlineInputBorder()),
              ),
            ],
            const SizedBox(height: 16),
            Text('I am a…', style: text.titleSmall),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final e in profiles.entries)
                  ChoiceChip(
                    label: Text(e.value),
                    selected: _profile == e.key,
                    onSelected: (_) => setState(() => _profile = e.key),
                  ),
              ],
            ),
            if (_error != null) ...[
              const SizedBox(height: 16),
              Text(_error!, style: TextStyle(color: scheme.error)),
            ],
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _busy ? null : _submit,
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
              child: _busy
                  ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2))
                  : Text(_joining ? 'Join team' : 'Create team'),
            ),
          ],
        ),
      ),
    );
  }
}
