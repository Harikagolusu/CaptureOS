import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../screens/main_shell.dart';
import '../screens/onboarding_screen.dart';
import '../theme/app_theme.dart';

class CaptureOSApp extends StatelessWidget {
  const CaptureOSApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CaptureOS',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      home: const _Root(),
    );
  }
}

/// Loads the saved login, then shows onboarding (create/join team) or the main tabs.
class _Root extends StatefulWidget {
  const _Root();

  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  final api = ApiClient.instance;
  late final Future<void> _loading = api.load();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<void>(
      future: _loading,
      builder: (context, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return const Scaffold(body: Center(child: CircularProgressIndicator()));
        }
        return api.isLoggedIn
            ? MainShell(onLogout: () => setState(() {}))
            : OnboardingScreen(onDone: () => setState(() {}));
      },
    );
  }
}
