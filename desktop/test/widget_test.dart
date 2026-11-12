import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lofiq_desktop/main.dart';
import 'package:lofiq_desktop/playback/equalizer/equalizer_controller.dart';

void main() {
  testWidgets('LofiQ desktop app boots into desktop shell', (WidgetTester tester) async {
    // Set standard desktop physical size
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    final eq = EqualizerController.instance;
    await tester.pumpWidget(LofiQDesktopApp(equalizerController: eq));
    await tester.pump();

    // Verify LofiQ Desktop branding is rendered in Navigation Rail
    expect(find.text('LofiQ'), findsOneWidget);
    expect(find.text('DESKTOP V1.0.3'), findsOneWidget);
  });
}
