!include nsDialogs.nsh
!include FileFunc.nsh

!macro customPageAfterChangeDir
  Page custom NeutronStoragePage NeutronStorageLeave
!macroend

!macro customHeader
!ifndef BUILD_UNINSTALLER
  Var neutronDataRoot
  Var neutronStorageAction
  Var neutronStorageStatus
  Var neutronStorageResponse
  Var neutronStorageOverride
  Var neutronStorageSavedOverride
  Var neutronStorageSwitch
  Var neutronStoragePage
  Var neutronStorageEdit
  Var neutronStorageBrowse
  Var neutronStorageHandle
  Var neutronStorageParameters

  Function NeutronStorageInit
    InitPluginsDir
    File /oname=$PLUGINSDIR\neutron-storage.ps1 "${BUILD_RESOURCES_DIR}\storage-preflight.ps1"
    File /oname=$PLUGINSDIR\neutron-release.json "${BUILD_RESOURCES_DIR}\release.json"
    StrCpy $neutronStorageOverride "0"
    StrCpy $neutronStorageSwitch ""
    StrCpy $neutronDataRoot ""
    Push $0
    Push $1
    System::Call 'kernel32::SetLastError(i 0)'
    System::Call 'kernel32::GetEnvironmentVariableW(w "NEUTRON_DATA_ROOT", w .r0, i ${NSIS_MAX_STRLEN}) i .r1'
    ${If} $1 > 0
      StrCpy $neutronStorageOverride "1"
      StrCpy $neutronStorageSavedOverride $0
      StrCpy $neutronDataRoot $0
    ${Else}
      System::Call 'kernel32::GetLastError() i .r1'
      ${If} $1 != 203
        StrCpy $neutronStorageOverride "1"
        StrCpy $neutronStorageSavedOverride ""
      ${EndIf}
    ${EndIf}
    Pop $1
    Pop $0
    ${GetParameters} $neutronStorageParameters
    ClearErrors
    ${GetOptions} $neutronStorageParameters "/DATA=" $neutronStorageResponse
    ${IfNot} ${Errors}
      ${If} $neutronStorageOverride == "1"
        SetErrorLevel 112
        IfSilent +2
          MessageBox MB_OK|MB_ICONEXCLAMATION "Use either /DATA or NEUTRON_DATA_ROOT, not both. No data selection was changed."
        Quit
      ${EndIf}
      StrCpy $neutronDataRoot $neutronStorageResponse
    ${EndIf}
    ${If} $neutronStorageOverride == "1"
      StrCpy $neutronStorageSwitch "-ProcessOverride"
    ${EndIf}
  FunctionEnd

  Function NeutronStorageInvoke
    # Paths are UTF-16 files, not interpolated PowerShell code/arguments.
    FileOpen $neutronStorageHandle "$PLUGINSDIR\neutron-data.txt" w
    FileWriteUTF16LE $neutronStorageHandle "$neutronDataRoot"
    FileClose $neutronStorageHandle
    FileOpen $neutronStorageHandle "$PLUGINSDIR\neutron-install.txt" w
    FileWriteUTF16LE $neutronStorageHandle "$INSTDIR"
    FileClose $neutronStorageHandle
    FileOpen $neutronStorageHandle "$PLUGINSDIR\neutron-control.txt" w
    FileWriteUTF16LE $neutronStorageHandle "$APPDATA\Neutron Launcher"
    FileClose $neutronStorageHandle
    ClearErrors
    ExecWait '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\neutron-storage.ps1" -Action $neutronStorageAction -RequestDirectory "$PLUGINSDIR" -ReleaseFile "$PLUGINSDIR\neutron-release.json" -ResultFile "$PLUGINSDIR\neutron-result.txt" -CheckFile "$PLUGINSDIR\neutron-check.json" $neutronStorageSwitch' $neutronStorageStatus
    ${If} ${Errors}
      StrCpy $neutronStorageStatus "112"
      StrCpy $neutronStorageResponse "Storage preflight could not start. No successful installation was recorded."
      Return
    ${EndIf}
    ClearErrors
    FileOpen $neutronStorageHandle "$PLUGINSDIR\neutron-result.txt" r
    ${If} ${Errors}
      StrCpy $neutronStorageStatus "112"
      StrCpy $neutronStorageResponse "Storage preflight produced no result."
      Return
    ${EndIf}
    FileReadUTF16LE $neutronStorageHandle $neutronStorageResponse
    FileClose $neutronStorageHandle
  FunctionEnd

  Function NeutronStoragePage
    StrCpy $neutronStorageAction "Resolve"
    Call NeutronStorageInvoke
    ${If} $neutronStorageStatus == "0"
      StrCpy $neutronDataRoot $neutronStorageResponse
    ${EndIf}
    nsDialogs::Create 1018
    Pop $neutronStoragePage
    ${If} $neutronStoragePage == error
      Abort
    ${EndIf}
    !insertmacro MUI_HEADER_TEXT "Neutron-Datenordner" "Hier liegen Java, Spieldateien, Build-Daten und installierte Client-Versionen."
    ${NSD_CreateLabel} 0 0 100% 28u "Dieser Ordner ist vom Launcher-Programmordner getrennt. Waehlen Sie einen lokalen Ordner auf einem Laufwerk mit mindestens 3 GiB freiem Platz fuer einen neuen Build."
    Pop $neutronStorageHandle
    ${NSD_CreateDirRequest} 0 35u 78% 14u "$neutronDataRoot"
    Pop $neutronStorageEdit
    ${NSD_CreateBrowseButton} 80% 35u 20% 14u "Ordner..."
    Pop $neutronStorageBrowse
    ${NSD_OnClick} $neutronStorageBrowse NeutronStorageBrowse
    ${NSD_CreateLabel} 0 58u 100% 60u "Bestehende Installation: Beenden Sie Neutron und kopieren Sie den vollstaendigen bisherigen Datenordner zuerst auf das neue Laufwerk. Waehlen Sie diese Kopie; das Original bleibt erhalten. Es werden keine Daten automatisch verschoben. Ein separat eingetragenes Spielverzeichnis bleibt an seinem bisherigen Ort."
    Pop $neutronStorageHandle
    ${If} $neutronStorageOverride == "1"
      EnableWindow $neutronStorageEdit 0
      EnableWindow $neutronStorageBrowse 0
      ${NSD_CreateLabel} 0 123u 100% 20u "Prozess-Override aktiv: Der Datenpfad wird nicht dauerhaft gespeichert."
      Pop $neutronStorageHandle
    ${EndIf}
    nsDialogs::Show
  FunctionEnd

  Function NeutronStorageBrowse
    nsDialogs::SelectFolderDialog "Neutron-Datenordner auswaehlen" "$neutronDataRoot"
    Pop $neutronStorageResponse
    ${If} $neutronStorageResponse != error
      ${NSD_SetText} $neutronStorageEdit "$neutronStorageResponse"
    ${EndIf}
  FunctionEnd

  Function NeutronStorageLeave
    ${NSD_GetText} $neutronStorageEdit $neutronDataRoot
    StrCpy $neutronStorageAction "Validate"
    Call NeutronStorageInvoke
    ${If} $neutronStorageStatus != "0"
      MessageBox MB_OK|MB_ICONEXCLAMATION "$neutronStorageResponse"
      Abort
    ${EndIf}
    StrCpy $neutronDataRoot $neutronStorageResponse
  FunctionEnd

  Function NeutronStorageRestoreEnvironment
    ${If} $neutronStorageOverride == "1"
      System::Call 'kernel32::SetEnvironmentVariableW(w "NEUTRON_DATA_ROOT", w "$neutronStorageSavedOverride")'
    ${Else}
      System::Call 'kernel32::SetEnvironmentVariableW(w "NEUTRON_DATA_ROOT", p 0)'
    ${EndIf}
  FunctionEnd

  # Compiled before the builder's main install section. This also runs in /S
  # mode, before uninstallOldVersion/installApplicationFiles can replace an EXE.
  Section "-Neutron storage preflight" NEUTRON_STORAGE_SECTION
    ${If} $neutronDataRoot == ""
      StrCpy $neutronStorageAction "Resolve"
      Call NeutronStorageInvoke
      ${If} $neutronStorageStatus == "0"
        StrCpy $neutronDataRoot $neutronStorageResponse
      ${EndIf}
    ${EndIf}
    StrCpy $neutronStorageAction "Check"
    SetDetailsView show
    DetailPrint "Checking Neutron data folder before replacing launcher files..."
    Call NeutronStorageInvoke
    ${If} $neutronStorageStatus != "0"
      SetErrorLevel 112
      IfSilent +2
        MessageBox MB_OK|MB_ICONEXCLAMATION "$neutronStorageResponse"
      Quit
    ${EndIf}
    StrCpy $neutronDataRoot $neutronStorageResponse
  SectionEnd
!endif
!macroend
