import 'package:flutter/material.dart';
import '../screens/home_screen.dart';
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
      home: const HomeScreen(),
    );
  }
}
