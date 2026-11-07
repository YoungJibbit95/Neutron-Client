import type { BrowserWindowConstructorOptions } from 'electron';

export function windowOptions(preload: string): BrowserWindowConstructorOptions {
  return {
    width: 1180, height: 780, minWidth: 960, minHeight: 640,
    title: 'Neutron Launcher', backgroundColor: '#171719',
    autoHideMenuBar: true, frame: true,
    webPreferences: { preload, contextIsolation: true, sandbox: true, nodeIntegration: false, webviewTag: false },
  };
}

export function allowsPermission(permission: string, url: string, entry: string) {
  return permission === 'clipboard-sanitized-write' && url === entry;
}
