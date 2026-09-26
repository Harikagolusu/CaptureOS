import 'package:flutter/material.dart';

import '../core/api_client.dart';

/// Image from the backend (photos need the login header).
class AuthImage extends StatelessWidget {
  const AuthImage(this.path, {super.key, this.fit = BoxFit.cover});

  final String path;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    final api = ApiClient.instance;
    final scheme = Theme.of(context).colorScheme;
    return Image.network(
      api.url(path),
      headers: api.authHeaders,
      fit: fit,
      errorBuilder: (_, _, _) => Container(
        color: scheme.surfaceContainerHighest,
        child: Icon(Icons.broken_image_outlined, color: scheme.outline),
      ),
      loadingBuilder: (context, child, progress) => progress == null
          ? child
          : Container(color: scheme.surfaceContainerHighest, child: const Center(child: CircularProgressIndicator(strokeWidth: 2))),
    );
  }
}
