import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';

/// The draggable bubble shown over Zoom / Meet / Teams / any app while CaptureOS records.
/// Runs in its own Flutter engine; tapping it tells the app to stop and upload.
class RecordingBubble extends StatefulWidget {
  const RecordingBubble({super.key});

  @override
  State<RecordingBubble> createState() => _RecordingBubbleState();
}

class _RecordingBubbleState extends State<RecordingBubble> {
  final _started = DateTime.now();
  late final Timer _timer = Timer.periodic(const Duration(seconds: 1), (_) => setState(() {}));
  bool _stopping = false;

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  Future<void> _stop() async {
    setState(() => _stopping = true);
    await FlutterOverlayWindow.shareData('stop');
    await FlutterOverlayWindow.closeOverlay();
  }

  @override
  Widget build(BuildContext context) {
    final d = DateTime.now().difference(_started);
    final time = '${d.inMinutes.toString().padLeft(2, '0')}:${d.inSeconds.remainder(60).toString().padLeft(2, '0')}';
    return Material(
      color: Colors.transparent,
      child: Center(
        child: GestureDetector(
          onTap: _stopping ? null : _stop,
          child: Container(
            width: 76,
            height: 76,
            decoration: BoxDecoration(
              color: const Color(0xFFD32F2F),
              shape: BoxShape.circle,
              boxShadow: const [BoxShadow(blurRadius: 10, color: Colors.black38)],
              border: Border.all(color: Colors.white, width: 3),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(_stopping ? Icons.hourglass_top : Icons.stop_rounded, color: Colors.white, size: 30),
                Text(time, style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
