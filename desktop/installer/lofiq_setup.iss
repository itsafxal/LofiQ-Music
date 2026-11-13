; =====================================================================
; LofiQ Desktop Music Player - Production Inno Setup Script
; Official Version: 1.0.4 (Build 67)
; Target Platform: Windows 10 / Windows 11 (64-bit x64)
; =====================================================================

#ifndef MyAppVersion
  #define MyAppVersion "1.0.4"
#endif
#ifndef MyAppVersionNum
  #define MyAppVersionNum "1.0.4.67"
#endif

#define MyAppName "LofiQ"
#define MyAppPublisher "LofiQ Team"
#define MyAppURL "https://lofiq-web.vercel.app/"
#define MyAppExeName "LofiQ.exe"
#define MyAppId "{{A8D3F912-7C3A-4E1B-95F8-180D825E6A3B}}"
#define MyAppUserModelId "com.lofiq.lofiq_desktop"

[Setup]
AppId={#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=..\build\installer
OutputBaseFilename=LofiQ-v{#MyAppVersion}-Windows-Setup
SetupIconFile=..\windows\runner\resources\app_icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes
CloseApplications=yes
CloseApplicationsFilter=*.exe
VersionInfoVersion={#MyAppVersionNum}
VersionInfoTextVersion={#MyAppVersion}
VersionInfoProductVersion={#MyAppVersionNum}
VersionInfoProductTextVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Desktop Music Player Setup
VersionInfoCopyright=Copyright (C) 2026 {#MyAppPublisher}. All rights reserved.
VersionInfoProductName={#MyAppName}

#ifdef SignInstaller
SignTool=custom_sign_tool
SignedUninstaller=yes
#else
SignedUninstaller=no
#endif

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "autostart"; Description: "Launch LofiQ automatically on Windows startup"; GroupDescription: "Additional Options:"; Flags: unchecked

[Files]
; Primary Windows Executable & Core Dependencies
Source: "..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

; Fallback ensure for root native libmpv-2.dll if not present in runner Release folder
Source: "..\libmpv-2.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; AppUserModelID: "{#MyAppUserModelId}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon; AppUserModelID: "{#MyAppUserModelId}"
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: autostart; AppUserModelID: "{#MyAppUserModelId}"

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
