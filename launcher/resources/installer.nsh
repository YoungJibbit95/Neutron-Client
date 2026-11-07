!macro customInit
  ${If} ${UAC_IsAdmin}
    SetErrorLevel 740
    IfSilent +2
      MessageBox MB_OK|MB_ICONEXCLAMATION "Run this Alpha installer normally in your own account, without Run as administrator."
    Quit
  ${EndIf}
  ${If} $installMode == "all"
    SetErrorLevel 1
    IfSilent +2
      MessageBox MB_OK|MB_ICONEXCLAMATION "This Alpha supports installation for your own Windows account only. All-users installation is refused."
    Quit
  ${EndIf}
  StrCpy $hasPerMachineInstallation "0"
  StrCpy $hasPerUserInstallation "1"
  !insertmacro setInstallModePerUser
!macroend

!macro customInstallMode
  StrCpy $isForceCurrentInstall "1"
!macroend

!macro customInstall
  DetailPrint "Preparing the pinned Neutron Alpha client. First setup needs internet and may take several minutes."
  DetailPrint "Java, game files and the client remain in your launcher data folder. Personal profiles are retained."
  SetDetailsView show
  ClearErrors
  ExecWait '"$INSTDIR\${APP_EXECUTABLE_FILENAME}" --prepare-alpha' $0
  ${If} ${Errors}
    SetErrorLevel 1
    Abort "Neutron preparation could not start. Close Neutron Launcher and run this installer again."
  ${EndIf}
  ${If} $0 != 0
    SetErrorLevel 1
    IfSilent +2
      MessageBox MB_OK|MB_ICONEXCLAMATION "Launcher files were installed, but client preparation did not finish (exit $0). Close the launcher and rerun setup. See %APPDATA%\Neutron Launcher\logs. The previous selected client and profiles are retained; the launcher EXE has already been updated."
    Abort "Client preparation failed after launcher installation. Rerun setup. Profiles and the previous selected client are retained."
  ${EndIf}
  DetailPrint "Neutron Alpha installed and verified. Playing uses the local client without Maven."
!macroend
