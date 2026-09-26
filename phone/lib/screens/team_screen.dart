import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/api_client.dart';
import '../models/api_models.dart';
import '../services/voice_service.dart';
import 'onboarding_screen.dart' show profiles;

/// Team (members, join code for admins), my profile type, voice intro, server, logout.
class TeamScreen extends StatefulWidget {
  const TeamScreen({super.key, required this.onLogout});

  final VoidCallback onLogout;

  @override
  State<TeamScreen> createState() => _TeamScreenState();
}

class _TeamScreenState extends State<TeamScreen> {
  final api = ApiClient.instance;
  late Future<Map<String, dynamic>> _future = _load();

  Future<Map<String, dynamic>> _load() async => Map<String, dynamic>.from(await api.get('/team') as Map);

  Future<void> _refresh() async {
    setState(() => _future = _load());
    await _future;
  }

  Future<void> _addMember() async {
    final name = TextEditingController();
    final nick = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add member'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: name, autofocus: true, decoration: const InputDecoration(labelText: 'Name')),
          TextField(controller: nick, decoration: const InputDecoration(labelText: 'Nicknames (optional)', hintText: 'Hritik, HK')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Add')),
        ],
      ),
    );
    if (ok != true || name.text.trim().isEmpty) return;
    await api.send('POST', '/team/members', {
      'name': name.text.trim(),
      'aliases': nick.text.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList(),
    });
    _refresh();
  }

  Future<void> _changeProfile(String p) async {
    try {
      await api.setProfile(p);
      setState(() {});
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Team & me')),
      body: FutureBuilder<Map<String, dynamic>>(
        future: _future,
        builder: (context, snap) {
          final team = snap.data;
          final members = (team?['members'] as List? ?? const [])
              .map((e) => TeamMember.fromJson(Map<String, dynamic>.from(e as Map)))
              .toList();
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              children: [
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: CircleAvatar(child: Text(api.userName.isEmpty ? '?' : api.userName[0])),
                  title: Text(api.userName, style: text.titleMedium),
                  subtitle: Text('${api.isAdmin ? 'Admin' : 'Member'} · ${team?['name'] ?? ''}'),
                ),
                if (api.isAdmin && team?['code'] != null)
                  Card(
                    color: scheme.primaryContainer,
                    child: ListTile(
                      title: const Text('Team code'),
                      subtitle: Text(team!['code'] as String,
                          style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w800, letterSpacing: 4)),
                      trailing: IconButton(
                        tooltip: 'Copy',
                        icon: const Icon(Icons.copy),
                        onPressed: () => Clipboard.setData(ClipboardData(text: team['code'] as String)),
                      ),
                    ),
                  ),
                const SizedBox(height: 12),
                Text('I am a…', style: text.titleSmall),
                const SizedBox(height: 6),
                Wrap(spacing: 8, runSpacing: 8, children: [
                  for (final e in profiles.entries)
                    ChoiceChip(
                      label: Text(e.value),
                      selected: api.user?['profile'] == e.key,
                      onSelected: (_) => _changeProfile(e.key),
                    ),
                ]),
                const SizedBox(height: 4),
                Text('The smart inbox uses this to decide what matters.', style: text.bodySmall),
                if (api.isAdmin) ...[
                  const Divider(height: 32),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.record_voice_over_outlined),
                    title: const Text('My voice intro'),
                    subtitle: const Text('10 s once, so meetings label your voice with your name'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const _VoiceIntroScreen())),
                  ),
                ],
                const Divider(height: 32),
                Row(children: [
                  Text('Members', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                  const Spacer(),
                  if (api.isAdmin) TextButton.icon(onPressed: _addMember, icon: const Icon(Icons.person_add_alt), label: const Text('Add')),
                ]),
                if (snap.connectionState != ConnectionState.done) const LinearProgressIndicator(),
                if (snap.hasError) Text(snap.error.toString(), style: TextStyle(color: scheme.error)),
                for (final m in members)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: CircleAvatar(child: Text(m.name[0].toUpperCase())),
                    title: Text(m.name + (m.aliases.isEmpty ? '' : '  (${m.aliases.join(', ')})')),
                    subtitle: Text('${m.role} · ${profiles[m.profile] ?? m.profile}'),
                    trailing: m.joined ? const Icon(Icons.phone_android, size: 18) : const Text('not joined'),
                  ),
                const Divider(height: 32),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.dns_outlined),
                  title: const Text('Server'),
                  subtitle: Text(api.baseUrl),
                ),
                OutlinedButton.icon(
                  onPressed: () async {
                    await api.logout();
                    widget.onLogout();
                  },
                  icon: const Icon(Icons.logout),
                  label: const Text('Log out'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _VoiceIntroScreen extends StatefulWidget {
  const _VoiceIntroScreen();

  @override
  State<_VoiceIntroScreen> createState() => _VoiceIntroScreenState();
}

class _VoiceIntroScreenState extends State<_VoiceIntroScreen> {
  final _voice = VoiceServiceImpl();
  final _role = TextEditingController(text: 'manager');
  bool _recording = false;
  bool _saving = false;
  String? _msg;

  @override
  void dispose() {
    _voice.dispose();
    super.dispose();
  }

  Future<void> _toggle() async {
    final api = ApiClient.instance;
    if (!_recording) {
      if (await _voice.startRecording('')) setState(() => _recording = true);
      return;
    }
    final path = await _voice.stopRecording();
    setState(() {
      _recording = false;
      _saving = true;
    });
    try {
      await api.upload('/voice/enroll', {'audio': [path!]}, fields: {'name': api.userName, 'role': _role.text.trim()});
      setState(() => _msg = 'Saved. Your voice will be labelled "${api.userName}" in meetings.');
    } on ApiException catch (e) {
      setState(() => _msg = e.message);
    } finally {
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final name = ApiClient.instance.userName;
    return Scaffold(
      appBar: AppBar(title: const Text('Voice intro')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(children: [
          TextField(controller: _role, decoration: const InputDecoration(labelText: 'Your role', border: OutlineInputBorder())),
          const SizedBox(height: 24),
          Text('Tap, then say:\n"Hi, I\'m $name, I\'m the ${_role.text}."',
              textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium),
          const Spacer(),
          if (_msg != null) Text(_msg!, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          _saving
              ? const CircularProgressIndicator()
              : FloatingActionButton.large(
                  onPressed: _toggle,
                  backgroundColor: _recording ? Theme.of(context).colorScheme.error : null,
                  child: Icon(_recording ? Icons.stop : Icons.mic, size: 40),
                ),
          const SizedBox(height: 32),
        ]),
      ),
    );
  }
}
