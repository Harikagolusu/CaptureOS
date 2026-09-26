import 'package:flutter/material.dart';

import 'assistant_screen.dart';
import 'inbox_screen.dart';
import 'tasks_screen.dart';

/// Logged-in app: Assistant (record / type), Photos (useful ones only), Tasks.
/// Team & settings open from the avatar on the Assistant screen.
class MainShell extends StatefulWidget {
  const MainShell({super.key, required this.onLogout});

  final VoidCallback onLogout;

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    final pages = [
      AssistantScreen(onLogout: widget.onLogout),
      const InboxScreen(),
      const TasksScreen(),
    ];
    return Scaffold(
      body: pages[_tab], // rebuilt on every tab switch so each tab loads fresh data
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.forum_outlined), selectedIcon: Icon(Icons.forum), label: 'Assistant'),
          NavigationDestination(icon: Icon(Icons.photo_library_outlined), selectedIcon: Icon(Icons.photo_library), label: 'Photos'),
          NavigationDestination(icon: Icon(Icons.check_circle_outline), selectedIcon: Icon(Icons.check_circle), label: 'Tasks'),
        ],
      ),
    );
  }
}
