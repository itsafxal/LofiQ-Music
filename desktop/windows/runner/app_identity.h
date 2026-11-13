#ifndef RUNNER_APP_IDENTITY_H_
#define RUNNER_APP_IDENTITY_H_

// Single process identity for the Windows desktop shell: taskbar grouping,
// notification center, and System Media Transport Controls (SMTC) source label.
// Without an explicit AppUserModelID, the SMTC volume flyout renders this app
// as "Unknown app" with a blank placeholder icon.
//
// This ID MUST match:
// 1. Inno Setup shortcut AppUserModelID (windows/installer/lofiq_setup.iss [Icons])
// 2. Window property store ID set in flutter_window.cpp
// 3. Process identity set via SetCurrentProcessExplicitAppUserModelID in main.cpp
constexpr wchar_t kLofiQAppUserModelId[] = L"com.lofiq.lofiq_desktop";

#endif  // RUNNER_APP_IDENTITY_H_
