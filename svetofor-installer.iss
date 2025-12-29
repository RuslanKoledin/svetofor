; Скрипт Inno Setup для установщика клиента Светофор JIRA
; Требуется Inno Setup 6.0 или выше: https://jrsoftware.org/isinfo.php

#define MyAppName "Светофор JIRA"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "ITSMJIRA"
#define MyAppExeName "svetooofor-client.jar"

[Setup]
; Основные настройки приложения
AppId={{B5F8E9A2-3C4D-4E1F-9A7B-6C8D5E4F3A2B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Svetofor
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=installer\output
OutputBaseFilename=svetofor-setup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
UninstallDisplayIcon={app}\icon.ico
SetupIconFile=src\main\resources\44_85245.ico

; Языки
[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

; Задачи (опции при установке)
[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; GroupDescription: "Дополнительные ярлыки:"; Flags: unchecked
Name: "startup"; Description: "Запускать при входе в систему"; GroupDescription: "Автозапуск:"

; Файлы для установки
[Files]
Source: "target\svetooofor-client.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "client.properties"; DestDir: "{app}"; Flags: ignoreversion
Source: "src\main\resources\44_85245.ico"; DestDir: "{app}"; DestName: "icon.ico"; Flags: ignoreversion

; Ярлыки
[Icons]
; Ярлык в меню Пуск
Name: "{group}\{#MyAppName}"; Filename: "javaw.exe"; Parameters: "-jar ""{app}\{#MyAppExeName}"""; WorkingDir: "{app}"; IconFilename: "{app}\icon.ico"

; Ярлык на рабочем столе (опционально)
Name: "{autodesktop}\{#MyAppName}"; Filename: "javaw.exe"; Parameters: "-jar ""{app}\{#MyAppExeName}"""; WorkingDir: "{app}"; IconFilename: "{app}\icon.ico"; Tasks: desktopicon

; Ярлык в автозагрузке (опционально)
Name: "{userstartup}\{#MyAppName}"; Filename: "javaw.exe"; Parameters: "-jar ""{app}\{#MyAppExeName}"""; WorkingDir: "{app}"; IconFilename: "{app}\icon.ico"; Tasks: startup

; Действия после установки
[Run]
; Опция запустить приложение после установки
Filename: "javaw.exe"; Parameters: "-jar ""{app}\{#MyAppExeName}"""; WorkingDir: "{app}"; Description: "Запустить {#MyAppName}"; Flags: nowait postinstall skipifsilent

; Проверка наличия Java перед установкой
[Code]
function InitializeSetup(): Boolean;
var
  ResultCode: Integer;
  JavaVersion: String;
begin
  Result := True;

  // Проверяем наличие Java
  if Exec('cmd.exe', '/c java -version 2>&1 | findstr "version"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    if ResultCode <> 0 then
    begin
      if MsgBox('Java не обнаружена на вашем компьютере.' + #13#10 +
                'Для работы приложения требуется Java 17 или выше.' + #13#10 + #13#10 +
                'Продолжить установку?', mbError, MB_YESNO) = IDNO then
      begin
        Result := False;
      end;
    end;
  end;
end;

// Сообщение после установки
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    // Можно добавить дополнительные действия после установки
  end;
end;
