import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

/// Opens the Android share sheet (pick vivo Notes, WhatsApp, Gmail…).
Future<void> shareText(String text, {String? subject}) =>
    SharePlus.instance.share(ShareParams(text: text, subject: subject));

Future<void> shareFile(String path, {String? text}) =>
    SharePlus.instance.share(ShareParams(files: [XFile(path)], text: text));

Future<void> openLink(String url) => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
