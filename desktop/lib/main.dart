import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'core/database/local_storage.dart';
import 'core/preferences/preferences_manager.dart';
import 'core/theme/colors.dart';
import 'playback/playback_controller.dart';
import 'playback/equalizer/equalizer_controller.dart';
import 'playback/smtc_manager.dart';
import 'ui/navigation/desktop_shell.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize native multimedia backend (libmpv)
  try {
    MediaKit.ensureInitialized();
  } catch (e) {
    debugPrint('MediaKit initialization error: $e');
  }

  // Initialize storage, preferences, and state machine
  await LocalStorage.instance.init();
  await PreferencesManager.instance.init();
  await PlaybackController.instance.init();

  // Initialize Windows System Media Transport Controls (SMTC) & Hardware Media Keys
  await SMTCManager.instance.init();

  final equalizerController = EqualizerController.instance;

  runApp(LofiQDesktopApp(equalizerController: equalizerController));
}

class LofiQDesktopApp extends StatelessWidget {
  final EqualizerController equalizerController;

  const LofiQDesktopApp({
    super.key,
    required this.equalizerController,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LofiQ Music - Windows Desktop',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: LofiQColors.backgroundBlack,
        colorScheme: const ColorScheme.dark(
          primary: LofiQColors.brandPurple,
          secondary: LofiQColors.brandTeal,
          surface: LofiQColors.surfaceBlack,
          background: LofiQColors.backgroundBlack,
        ),
        fontFamily: 'Segoe UI',
        useMaterial3: true,
      ),
      home: DesktopShell(equalizerController: equalizerController),
    );
  }
}
