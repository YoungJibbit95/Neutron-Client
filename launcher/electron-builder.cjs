module.exports = {
  appId: 'dev.neutron.launcher', productName: 'Neutron Launcher',
  artifactName: 'Neutron-Launcher-${version}-Windows-x64.${ext}',
  directories: { output: process.env.NEUTRON_PACKAGE_OUTPUT || 'release', buildResources: 'resources' },
  files: ['dist/**/*', 'out-electron/**/*', 'package.json'],
  extraResources: [{ from: 'resources/source-adapter', to: 'source-adapter' }, { from: 'resources/release.json', to: 'release.json' }],
  asar: true, npmRebuild: false, nodeGypRebuild: false,
  win: { target: [{ target: 'nsis', arch: ['x64'] }], icon: 'resources/icon.ico', requestedExecutionLevel: 'asInvoker' },
  nsis: { include: 'resources/installer.nsh', oneClick: false, perMachine: false, allowElevation: false, packElevateHelper: false, allowToChangeInstallationDirectory: true, deleteAppDataOnUninstall: false, runAfterFinish: false, createDesktopShortcut: false, createStartMenuShortcut: true, differentialPackage: false },
  publish: null,
};
