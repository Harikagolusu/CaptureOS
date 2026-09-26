import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api_client.dart';
import '../models/api_models.dart';
import '../widgets/auth_image.dart';
import '../widgets/empty_state.dart';
import '../widgets/error_state.dart';
import '../widgets/loading_state.dart';

/// Smart inbox: the AI sorts gallery photos and recordings into albums and pulls out to-dos.
class InboxScreen extends StatefulWidget {
  const InboxScreen({super.key});

  @override
  State<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends State<InboxScreen> {
  final api = ApiClient.instance;
  late Future<(List<Album>, List<InboxItem>)> _future = _load();
  String? _scanStatus;

  Future<(List<Album>, List<InboxItem>)> _load() async {
    final albums = (await api.get('/inbox/albums') as List).map((e) => Album.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    final items = (await api.get('/inbox') as List).map((e) => InboxItem.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    albums.sort((a, b) => b.count.compareTo(a.count));
    return (albums, items);
  }

  Future<void> _refresh() async {
    setState(() => _future = _load());
    await _future;
  }

  /// Sends photos taken since the last scan (default: today), shrunk to 1600 px. The server skips repeats.
  Future<void> _scanGallery() async {
    final perm = await PhotoManager.requestPermissionExtend();
    if (!perm.hasAccess) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Allow photo access to scan the gallery')));
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    final now = DateTime.now();
    final since = DateTime.tryParse(prefs.getString('last_scan') ?? '') ?? DateTime(now.year, now.month, now.day);
    final filter = FilterOptionGroup(createTimeCond: DateTimeCond(min: since, max: now));
    final paths = await PhotoManager.getAssetPathList(type: RequestType.image, onlyAll: true, filterOption: filter);
    final assets = paths.isEmpty ? <AssetEntity>[] : await paths.first.getAssetListRange(start: 0, end: 40);
    if (assets.isEmpty) {
      setState(() => _scanStatus = 'No new photos since ${since.hour}:${since.minute.toString().padLeft(2, '0')}');
      return;
    }
    final tmp = await getTemporaryDirectory();
    var sent = 0;
    for (var i = 0; i < assets.length; i += 5) {
      final batch = assets.skip(i).take(5).toList();
      final files = <String>[];
      for (final a in batch) {
        final bytes = await a.thumbnailDataWithSize(const ThumbnailSize(1600, 1600), quality: 85);
        if (bytes == null) continue;
        final f = File('${tmp.path}/scan_${a.id.replaceAll(RegExp(r'[^A-Za-z0-9]'), '_')}.jpg');
        await f.writeAsBytes(bytes);
        files.add(f.path);
      }
      setState(() => _scanStatus = 'Sending ${sent + files.length} of ${assets.length}…');
      try {
        await api.upload('/inbox', {'files': files}, fields: {
          'client_ids': batch.map((a) => a.id).join(','),
          'taken_at': batch.map((a) => a.createDateTime.toIso8601String()).join(','),
        });
        sent += files.length;
      } on ApiException catch (e) {
        setState(() => _scanStatus = 'Stopped: ${e.message}');
        return;
      }
    }
    await prefs.setString('last_scan', now.toIso8601String());
    setState(() => _scanStatus = 'Sent $sent photos — the AI is sorting them');
    Future.delayed(const Duration(seconds: 8), _refresh);
  }

  Future<void> _pickFiles() async {
    final picked = await FilePicker.pickFiles(type: FileType.media);
    final paths = picked.map((f) => f.path).whereType<String>().toList();
    if (paths.isEmpty) return;
    setState(() => _scanStatus = 'Sending ${paths.length} file(s)…');
    try {
      await api.upload('/inbox', {'files': paths});
      setState(() => _scanStatus = 'Sent — the AI is sorting them');
      Future.delayed(const Duration(seconds: 8), _refresh);
    } on ApiException catch (e) {
      setState(() => _scanStatus = e.message);
    }
  }

  void _openAlbum(String name, List<InboxItem> items) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => _AlbumScreen(name: name, items: items.where((i) => i.album == name).toList()),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Smart inbox'),
        actions: [IconButton(tooltip: 'Add photos or recordings', icon: const Icon(Icons.add_photo_alternate_outlined), onPressed: _pickFiles)],
      ),
      body: FutureBuilder<(List<Album>, List<InboxItem>)>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const LoadingState(message: 'Loading inbox...');
          if (snap.hasError) return ErrorState(message: snap.error.toString(), onRetry: _refresh);
          final (albums, items) = snap.data!;
          final important = items.where((i) => i.actionable).take(10).toList();
          final pending = items.where((i) => i.status == 'queued').length;
          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              children: [
                FilledButton.icon(
                  onPressed: _scanGallery,
                  icon: const Icon(Icons.auto_awesome),
                  label: const Text("Check today's photos"),
                  style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                ),
                if (_scanStatus != null || pending > 0)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text([?_scanStatus, if (pending > 0) '$pending still sorting…'].join(' · '),
                        style: text.bodySmall),
                  ),
                const SizedBox(height: 8),
                Text('Tip: photos and recordings sent to the laptop with Office Kit land here too.', style: text.bodySmall),
                if (items.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 48),
                    child: EmptyState(
                      icon: Icons.photo_library_outlined,
                      title: 'Nothing sorted yet',
                      subtitle: 'Check today\'s photos, or send some from the gallery.',
                    ),
                  ),
                if (important.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  Text('Needs attention', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 8),
                  for (final i in important) _ItemTile(item: i),
                ],
                if (albums.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  Text('Albums', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 8),
                  GridView.count(
                    crossAxisCount: 2,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                    children: [
                      for (final a in albums)
                        InkWell(
                          onTap: () => _openAlbum(a.name, items),
                          borderRadius: BorderRadius.circular(14),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(14),
                            child: Stack(fit: StackFit.expand, children: [
                              _Cover(item: items.where((i) => i.album == a.name).firstOrNull),
                              Container(
                                alignment: Alignment.bottomLeft,
                                padding: const EdgeInsets.all(10),
                                decoration: const BoxDecoration(
                                  gradient: LinearGradient(begin: Alignment.center, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black87]),
                                ),
                                child: Text('${a.name}\n${a.count} item${a.count == 1 ? '' : 's'}',
                                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                              ),
                            ]),
                          ),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}

class _Cover extends StatelessWidget {
  const _Cover({required this.item});
  final InboxItem? item;
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (item != null && item!.isPhoto) return AuthImage(item!.fileUrl);
    return Container(color: scheme.secondaryContainer, child: Icon(Icons.graphic_eq, size: 48, color: scheme.onSecondaryContainer));
  }
}

class _ItemTile extends StatelessWidget {
  const _ItemTile({required this.item});
  final InboxItem item;
  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: SizedBox(width: 48, height: 48, child: ClipRRect(borderRadius: BorderRadius.circular(8), child: _Cover(item: item))),
        title: Text(item.title.isEmpty ? 'Sorting…' : item.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(
          [item.album, if (item.taskIds.isNotEmpty) '${item.taskIds.length} task${item.taskIds.length == 1 ? '' : 's'}', if (item.meetingId != null) 'meeting']
              .where((e) => e.isNotEmpty)
              .join(' · '),
        ),
        onTap: () => showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          showDragHandle: true,
          builder: (_) => _ItemSheet(item: item),
        ),
      ),
    );
  }
}

class _ItemSheet extends StatelessWidget {
  const _ItemSheet({required this.item});
  final InboxItem item;
  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.75,
      builder: (_, controller) => ListView(
        controller: controller,
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
        children: [
          if (item.isPhoto) ClipRRect(borderRadius: BorderRadius.circular(12), child: AuthImage(item.fileUrl, fit: BoxFit.contain)),
          const SizedBox(height: 12),
          Text(item.title, style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Wrap(spacing: 6, children: [
            Chip(label: Text(item.album), visualDensity: VisualDensity.compact),
            Chip(label: Text(item.category), visualDensity: VisualDensity.compact),
            if (item.source == 'officekit') const Chip(label: Text('via Office Kit'), visualDensity: VisualDensity.compact),
          ]),
          const SizedBox(height: 8),
          Text(item.summary),
          if (item.taskIds.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text('${item.taskIds.length} task(s) added to your list', style: TextStyle(color: Theme.of(context).colorScheme.primary)),
          ],
          if (item.text.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(item.isPhoto ? 'Text in the photo' : 'Transcript', style: text.titleSmall),
            const SizedBox(height: 6),
            SelectableText(item.text),
          ],
        ],
      ),
    );
  }
}

class _AlbumScreen extends StatelessWidget {
  const _AlbumScreen({required this.name, required this.items});
  final String name;
  final List<InboxItem> items;
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(name)),
      body: ListView(padding: const EdgeInsets.all(12), children: [for (final i in items) _ItemTile(item: i)]),
    );
  }
}
