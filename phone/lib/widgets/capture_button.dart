import 'package:flutter/material.dart';

class CaptureButton extends StatelessWidget {
  const CaptureButton({super.key, required this.onPressed, this.label = 'Start Capture'});

  final VoidCallback onPressed;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      child: SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: onPressed,
          icon: const Icon(Icons.add_a_photo_outlined),
          label: Text(label),
        ),
      ),
    );
  }
}
