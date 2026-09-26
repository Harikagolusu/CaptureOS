import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class ApiException implements Exception {
  ApiException(this.statusCode, this.message);
  final int statusCode;
  final String message;

  bool get isUnauthorized => statusCode == 401;

  @override
  String toString() => message;
}

/// Talks to the CaptureOS backend (laptop or cloud). Keeps the server URL and login token.
class ApiClient {
  ApiClient._();
  static final ApiClient instance = ApiClient._();

  static const _kBaseUrl = 'base_url';
  static const _kToken = 'token';
  static const _kUser = 'user';
  static const _kTeam = 'team';

  String baseUrl = 'http://192.168.1.10:8000';
  String? token;
  Map<String, dynamic>? user;
  Map<String, dynamic>? team;

  bool get isLoggedIn => token != null && user != null;
  bool get isAdmin => user?['role'] == 'admin';
  String get userName => (user?['name'] as String?) ?? '';

  Map<String, String> get authHeaders => {if (token != null) 'Authorization': 'Bearer $token'};

  /// Absolute URL for a backend path like `/inbox/7/file` (used by Image.network with [authHeaders]).
  String url(String path) => path.startsWith('http') ? path : '$baseUrl$path';

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    baseUrl = prefs.getString(_kBaseUrl) ?? baseUrl;
    token = prefs.getString(_kToken);
    final u = prefs.getString(_kUser);
    final t = prefs.getString(_kTeam);
    user = u == null ? null : jsonDecode(u) as Map<String, dynamic>;
    team = t == null ? null : jsonDecode(t) as Map<String, dynamic>;
  }

  Future<void> setBaseUrl(String value) async {
    baseUrl = value.trim().replaceAll(RegExp(r'/+$'), '');
    if (!baseUrl.startsWith('http')) baseUrl = 'http://$baseUrl';
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kBaseUrl, baseUrl);
  }

  Future<void> _saveLogin(Map<String, dynamic> body) async {
    user = Map<String, dynamic>.from(body['user'] as Map);
    team = Map<String, dynamic>.from(body['team'] as Map);
    token = user!.remove('token') as String?;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kToken, token!);
    await prefs.setString(_kUser, jsonEncode(user));
    await prefs.setString(_kTeam, jsonEncode(team));
  }

  Future<void> logout() async {
    token = null;
    user = null;
    team = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_kToken);
    await prefs.remove(_kUser);
    await prefs.remove(_kTeam);
  }

  // ---------- low level ----------

  dynamic _decode(http.Response r) {
    final body = r.body.isEmpty ? null : jsonDecode(utf8.decode(r.bodyBytes));
    if (r.statusCode >= 400) {
      final detail = body is Map ? body['detail'] : null;
      throw ApiException(r.statusCode, detail is String ? detail : 'Server error ${r.statusCode}');
    }
    return body;
  }

  Future<dynamic> get(String path) async {
    try {
      final r = await http.get(Uri.parse(url(path)), headers: authHeaders).timeout(const Duration(seconds: 30));
      return _decode(r);
    } on SocketException {
      throw ApiException(0, "Can't reach the server at $baseUrl");
    }
  }

  Future<dynamic> send(String method, String path, [Map<String, dynamic>? body]) async {
    final req = http.Request(method, Uri.parse(url(path)))
      ..headers.addAll({...authHeaders, 'Content-Type': 'application/json'})
      ..body = jsonEncode(body ?? {});
    try {
      final r = await http.Response.fromStream(await req.send().timeout(const Duration(seconds: 60)));
      return _decode(r);
    } on SocketException {
      throw ApiException(0, "Can't reach the server at $baseUrl");
    }
  }

  /// Multipart upload. [files] maps a field name to one or more local file paths.
  Future<dynamic> upload(String path, Map<String, List<String>> files, {Map<String, String> fields = const {}}) async {
    final req = http.MultipartRequest('POST', Uri.parse(url(path)))
      ..headers.addAll(authHeaders)
      ..fields.addAll(fields);
    for (final entry in files.entries) {
      for (final p in entry.value) {
        req.files.add(await http.MultipartFile.fromPath(entry.key, p));
      }
    }
    try {
      final r = await http.Response.fromStream(await req.send().timeout(const Duration(minutes: 5)));
      return _decode(r);
    } on SocketException {
      throw ApiException(0, "Can't reach the server at $baseUrl");
    }
  }

  /// Downloads a protected file (e.g. the Word report) to [savePath].
  Future<File> download(String path, String savePath) async {
    final r = await http.get(Uri.parse(url(path)), headers: authHeaders);
    if (r.statusCode >= 400) throw ApiException(r.statusCode, 'Download failed');
    return File(savePath).writeAsBytes(r.bodyBytes);
  }

  // ---------- team & login ----------

  Future<bool> health() async {
    try {
      final r = await http.get(Uri.parse(url('/health'))).timeout(const Duration(seconds: 5));
      return r.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<void> createTeam(String teamName, String adminName, String profile, List<String> aliases) async {
    await _saveLogin(await send('POST', '/team',
        {'team_name': teamName, 'admin_name': adminName, 'profile': profile, 'aliases': aliases}) as Map<String, dynamic>);
  }

  Future<void> join(String code, String name, String profile) async {
    await _saveLogin(await send('POST', '/join', {'code': code, 'name': name, 'profile': profile}) as Map<String, dynamic>);
  }

  Future<Map<String, dynamic>> me() async => Map<String, dynamic>.from(await get('/me') as Map);

  Future<void> setProfile(String profile) async {
    user = Map<String, dynamic>.from(await send('PATCH', '/me', {'profile': profile}) as Map);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kUser, jsonEncode(user));
  }
}
