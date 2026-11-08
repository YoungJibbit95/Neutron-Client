!include "${BUILD_RESOURCES_DIR}\storage-installer.nsh"

!macro customInit
  ${If} ${UAC_IsAdmin}
    SetErrorLevel 740
    IfSilent +2
      MessageBox MB_OK|MB_ICONEXCLAMATION "Run this Neutron installer normally in your own account, without Run as administrator."
    Quit
  ${EndIf}
  ${If} $installMode == "all"
    SetErrorLevel 1
    IfSilent +2
      MessageBox MB_OK|MB_ICONEXCLAMATION "This Neutron supports installation for your own Windows account only. All-users installation is refused."
    Quit
  ${EndIf}
  StrCpy $hasPerMachineInstallation "0"
  StrCpy $hasPerUserInstallation "1"
  !insertmacro setInstallModePerUser
  Call NeutronStorageInit
!macroend

!macro customInstallMode
  StrCpy $isForceCurrentInstall "1"
!macroend

!macro customInstall
  DetailPrint "Preparing the pinned Neutron client. First setup needs internet and may take several minutes."
  DetailPrint "Java, game files and the client remain in your launcher data folder. Personal profiles are retained."
  SetDetailsView show
  System::Call 'kernel32::SetEnvironmentVariableW(w "NEUTRON_DATA_ROOT", w "$neutronDataRoot")'
  ClearErrors
  ExecWait '"$INSTDIR\${APP_EXECUTABLE_FILENAME}" --prepare-alpha' $0
  ${If} ${Errors}
    Call NeutronStorageRestoreEnvironment
    SetErrorLevel 1
    Abort "Neutron preparation could not start. Close Neutron Launcher and run this installer again."
  ${EndIf}
  ${If} $0 != 0
    Call NeutronStorageRestoreEnvironment
    SetErrorLevel 1
    IfSilent +2
      MessageBox MB_OK|MB_ICONEXCLAMATION "Launcher files were installed, but client preparation did not finish (exit $0). Close the launcher and rerun setup. See $neutronDataRoot\logs. The previous selected client and profiles are retained; the launcher EXE has already been updated."
    Abort "Client preparation failed after launcher installation. Rerun setup. Profiles and the previous selected client are retained."
  ${EndIf}
  Call NeutronStorageRestoreEnvironment
  StrCpy $neutronStorageAction "Commit"
  Call NeutronStorageInvoke
  ${If} $neutronStorageStatus != "0"
    SetErrorLevel 1
    IfSilent +2
      MessageBox MB_OK|MB_ICONEXCLAMATION "$neutronStorageResponse"
    Abort "The data selection could not be committed. Previous data remains preserved. Rerun setup."
  ${EndIf}
  DetailPrint "Neutron installed and verified. Playing uses the local client without Maven."
!macroend
