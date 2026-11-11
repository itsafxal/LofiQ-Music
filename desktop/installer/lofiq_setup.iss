; =====================================================================
; LofiQ Desktop Music Player - Production Inno Setup Script
; Official Version: 1.0.2 (Build 65)
; Target Platform: Windows 10 / Windows 11 (64-bit)
; =====================================================================

#define MyAppName "LofiQ"
#define MyAppVersion "1.0.2"
#define MyAppPublisher "LofiQ Team"
#define MyAppURL "https://lofiq-web.vercel.app/"
#define MyAppExeName "LofiQ.exe"
#define MyAppId "{{A8D3F912-7C3A-4E1B-95F8-180D825E6A3B}"

[Setup]
AppId={#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} V{#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=..\build\installer
OutputBaseFilename=LofiQ-v1.0.2-Windows-Setup
SetupIconFile=..\windows\runner\resources\app_icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes
VersionInfoVersion=1.0.2.65
VersionInfoCompany=LofiQ
VersionInfoDescription=LofiQ Desktop Music Player Installer
VersionInfoCopyright=Copyright (C) 2026 LofiQ. All rights reserved.
VersionInfoProductName=LofiQ
VersionInfoProductVersion=1.0.2.65

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "autostart"; Description: "Launch LofiQ automatically on Windows startup"; GroupDescription: "Additional Options:"; Flags: unchecked

[Files]
; Primary Windows Executable (LofiQ.exe)
Source: "..\build\windows\x64\runner\Release\LofiQ.exe"; DestDir: "{app}"; DestName: "{#MyAppExeName}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "..\build\windows\x64\runner\Release\desktop.exe"; DestDir: "{app}"; DestName: "{#MyAppExeName}"; Flags: ignoreversion skipifsourcedoesntexist

; Core Flutter Engine DLL
Source: "..\build\windows\x64\runner\Release\flutter_windows.dll"; DestDir: "{app}"; Flags: ignoreversion

; Native MediaKit and Libmpv Audio Engine Libraries
Source: "..\build\windows\x64\runner\Release\media_kit_native_event_loop.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "..\build\windows\x64\runner\Release\libmpv-2.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "..\libmpv-2.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

; Windows Native Plugin DLLs
Source: "..\build\windows\x64\runner\Release\screen_retriever_windows_plugin.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "..\build\windows\x64\runner\Release\window_manager_plugin.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "..\build\windows\x64\runner\Release\smtc_windows_plugin.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

; Flutter Data Assets and Unicode ICU Table
Source: "..\build\windows\x64\runner\Release\data\*"; DestDir: "{app}\data"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: autostart

[Registry]
; Register custom URI protocol (lofiq://) for Listen Together deep links
Root: HKCU; Subkey: "Software\Classes\lofiq"; ValueType: string; ValueName: ""; ValueData: "URL:LofiQ Protocol"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\lofiq"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\lofiq\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\lofiq\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Flags: uninsdeletekey

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
// Pre-flight check for Microsoft Visual C++ 2015-2022 Redistributable (x64)
function IsVCRedistInstalled: Boolean;
begin
  Result := RegKeyExists(HKLM, 'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\X64') or
            FileExists(ExpandConstant('{sys}\vcruntime140.dll'));
end;

function InitializeSetup: Boolean;
var
  ErrorCode: Integer;
begin
  Result := True;
  if not IsVCRedistInstalled then
  begin
    if MsgBox('LofiQ requires the Microsoft Visual C++ 2015-2022 Redistributable (x64).' + #13#10 +
              'Would you like to open the Microsoft download page now?', mbConfirmation, MB_YESNO) = IDYES then
    begin
      ShellExec('open', 'https://aka.ms/vs/17/release/vc_redist.x64.exe', '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
    end;
  end;
end;

