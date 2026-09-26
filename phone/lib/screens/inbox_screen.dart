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
import '../services/destinations.dart';

/// Smart inbox: the AI sorts gallery photos and recordings into albums and pulls out to-dos.
class InboxScreen extends StatefulWidget {
  const InboxScreen({super.key});

  @override
  State<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends State<InboxScreen> {
  final api = ApiClient.instance;
  late Future<List<InboxItem>> _future = _load();
  String? _scanStatus;

  /// Only photos the AI marked useful for this user (boards, notes, notices, receipts…).
  Future<List<InboxItem>> _load() async {
    final rows = await api.get('/inbox?kind=photo&actionable=true') as List;
    return rows.map((e) => InboxItem.fromJson(Map<String, dynamic>.from(e as Map))).toList();
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
    setState(() => _scanStatus = 'Sent $sent photos — only the useful ones will show up here');
    Future.delayed(const Duration(seconds: 12), _afterSort);
  }

  Future<void> _pickFiles() async {
    final picked = await FilePicker.pickFiles(type: FileType.media);
    final paths = picked.map((f) => f.path).whereType<String>().toList();
    if (paths.isEmpty) return;
    setState(() => _scanStatus = 'Sending ${paths.length} file(s)…');
    try {
      await api.upload('/inbox', {'files': paths});
      setState(() => _scanStatus = 'Sent — only the useful ones will show up here');
      Future.delayed(const Duration(seconds: 12), _afterSort);
    } on ApiException catch (e) {
      setState(() => _scanStatus = e.message);
    }
  }

  /// Refresh, and put any new dated tasks (e.g. "exam on Friday" from a notice) into the calendar.
  Future<void> _afterSort() async {
    if (!mounted) return;
    await _refresh();
    try {
      final added = await Destinations.syncMyTasksToCalendar();
      if (added > 0 && mounted) setState(() => _scanStatus = 'Added $added task(s) to your calendar');
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Useful photos'),
        actions: [IconButton(tooltip: 'Add photos or recordings', icon: const Icon(Icons.add_photo_alternate_outlined), onPressed: _pickFiles)],
      ),
      body: FutureBuilder<List<InboxItem>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const LoadingState(message: 'Loading photos...');
          if (snap.hasError) return ErrorState(message: snap.error.toString(), onRetry: _refresh);
          final items = snap.data!;
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
                if (_scanStatus != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_scanStatus!, style: text.bodySmall)),
                const SizedBox(height: 6),
                Text('Only photos that matter to you appear here — boards, notes, notices, receipts. '
                    'Photos sent to the laptop with Office Kit are checked too.', style: text.bodySmall),
                const SizedBox(height: 12),
                if (items.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 48),
                    child: EmptyState(
                      icon: Icons.photo_library_outlined,
                      title: 'No useful photos yet',
                      subtitle: "Snap a whiteboard, notice or notes, then check today's photos.",
                    ),
                  )
                else
                  GridView.count(
                    crossAxisCount: 2,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                    childAspectRatio: 0.8,
                    children: [for (final i in items) _PhotoCard(item: i)],
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _PhotoCard extends StatelessWidget {
  const _PhotoCard({required this.item});
  final InboxItem item;
  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          showDragHandle: true,
          builder: (_) => _ItemSheet(item: item),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(child: SizedBox(width: double.infinity, child: AuthImage(item.fileUrl))),
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
              Text(
                item.taskIds.isEmpty ? item.category : '${item.taskIds.length} task${item.taskIds.length == 1 ? '' : 's'}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ]),
          ),
        ]),
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

