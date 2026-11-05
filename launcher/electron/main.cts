import { app, BrowserWindow, dialog, ipcMain, net, protocol, session } from 'electron';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { mkdirSync } from 'node:fs';
import fs from 'node:fs/promises';
import { allowsPermission, windowOptions } from './window-contract.cjs';
import { parseCommand } from './contracts.cjs';
import { LauncherService } from './service.cjs';

protocol.registerSchemesAsPrivileged([{ scheme: 'neutron', privileges: { standard: true, secure: true, supportFetchAPI: true } }]);
app.setName('Neutron Launcher');
const dataRoot = process.env.NEUTRON_DATA_ROOT ?? (app.isPackaged ? path.join(app.getPath('appData'), 'Neutron Launcher') : '');
if (!dataRoot || !path.isAbsolute(dataRoot)) throw new Error('Development requires an absolute NEUTRON_DATA_ROOT on the chosen data drive.');
for (const directory of [dataRoot, ...['session', 'logs', 'crashes', 'temp'].map(name => path.join(dataRoot, name))]) mkdirSync(directory, { recursive: true });
app.setPath('userData', dataRoot); app.setPath('sessionData', path.join(dataRoot, 'session')); app.setPath('logs', path.join(dataRoot, 'logs')); app.setPath('crashDumps', path.join(dataRoot, 'crashes'));
process.env.TEMP = path.join(dataRoot, 'temp'); process.env.TMP = process.env.TEMP;
if (!app.requestSingleInstanceLock()) app.exit(0);
const service = new LauncherService(dataRoot, app.isPackaged ? path.join(process.resourcesPath, 'source-adapter') : path.join(__dirname, '../resources/source-adapter'), app.isPackaged ? path.dirname(process.execPath) : undefined);
const startedAt = performance.now();
const entry = 'neutron://launcher/index.html'; let window: BrowserWindow | null = null; let quitting = false; let shuttingDown = false;
let initializing = true; let initialized: Promise<unknown> = Promise.resolve();
const desktopSnapshot = () => initializing ? { ...service.snapshot(), status: 'prepare' as const } : service.snapshot();
const quit = async () => {
  if (shuttingDown) return; shuttingDown = true;
  try { await initialized.catch(() => {}); await service.shutdown(); quitting = true; app.quit(); }
  catch (error) { shuttingDown = false; service.setError(error); }
};
void app.whenReady().then(async () => {
  const renderer = path.resolve(__dirname, '../dist');
  protocol.handle('neutron', request => {
    const url = new URL(request.url); let relative: string;
    try { relative = decodeURIComponent(url.pathname).replace(/^\//, ''); } catch { return new Response('Bad path', { status: 400 }); }
    const target = path.resolve(renderer, relative);
    if (url.host !== 'launcher' || request.method !== 'GET' || relative.includes('\\') || path.relative(renderer, target).startsWith('..') || path.isAbsolute(path.relative(renderer, target))) return new Response('Forbidden', { status: 403 });
    return net.fetch(pathToFileURL(target).href);
  });
  session.defaultSession.setPermissionRequestHandler((contents, permission, callback) => callback(allowsPermission(permission, contents.getURL(), entry)));
  session.defaultSession.setPermissionCheckHandler((contents, permission) => !!contents && allowsPermission(permission, contents.getURL(), entry));
  window = new BrowserWindow({ ...windowOptions(path.join(__dirname, 'preload.cjs')), show: false });
  const startupWatchdog = setTimeout(() => { if (window && !window.isDestroyed() && !window.isVisible()) { dialog.showErrorBox('Neutron Launcher', 'The interface did not initialize. Please restart the launcher.'); app.exit(1); } }, 15000);
  startupWatchdog.unref();
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' })); window.webContents.on('will-navigate', event => event.preventDefault());
  window.on('close', event => {
    const state = service.snapshot();
    if (!quitting && (state.runningBuild || ['prepare', 'download', 'build', 'verify'].includes(state.status))) { event.preventDefault(); window?.webContents.send('neutron:close-request'); }
  });
  const validateSender = (event: Electron.IpcMainInvokeEvent | Electron.IpcMainEvent) => {
    if (!window || event.sender !== window.webContents || event.senderFrame !== window.webContents.mainFrame || event.senderFrame.url !== entry) throw new Error('Untrusted launcher IPC sender.');
  };
  let firstWindowMs: number | null = null; let verificationMs: number | null = null;
  const recordStartup = () => { if (firstWindowMs !== null && verificationMs !== null) void fs.writeFile(path.join(dataRoot, 'logs/startup.json'), JSON.stringify({ firstWindowMs, verificationMs })).catch(console.error); };
  ipcMain.on('neutron:renderer-ready', event => { try { validateSender(event); } catch { return; } if (firstWindowMs !== null) return; clearTimeout(startupWatchdog); firstWindowMs = Math.round(performance.now() - startedAt); window?.show(); recordStartup(); });
  ipcMain.handle('neutron:snapshot', event => { validateSender(event); return desktopSnapshot(); });
  ipcMain.handle('neutron:command', async (event, value: unknown) => {
    validateSender(event); const command = parseCommand(value);
    await initialized;
    try {
      switch (command.type) {
        case 'browse': {
          const picked = await dialog.showOpenDialog(window!, command.kind === 'game' ? { properties: ['openDirectory', 'createDirectory'] } : { properties: ['openFile'], filters: [{ name: 'Java 8', extensions: ['exe'] }] });
          if (picked.canceled || !picked.filePaths[0]) return service.snapshot();
          return await service.execute({ type: 'save-options', patch: command.kind === 'game' ? { gameDir: picked.filePaths[0] } : { java: 'custom', javaPath: picked.filePaths[0] } });
        }
        case 'export': {
          const text = await service.logs.export(command.source);
          const picked = await dialog.showSaveDialog(window!, { defaultPath: 'neutron-' + command.source + '-logs.txt', filters: [{ name: 'Text', extensions: ['txt'] }] });
          if (!picked.canceled && picked.filePath) await fs.writeFile(picked.filePath, text, 'utf8'); return picked.canceled ? '' : 'Export saved.';
        }
        case 'report': return service.report();
        case 'close': if (command.choice === 'minimize') window?.minimize(); else if (command.choice === 'quit') await quit(); return service.snapshot();
        default: return await service.execute(command);
      }
    } catch (error) { service.setError(error); throw error; }
  });
  service.on('snapshot', state => { if (window && !window.isDestroyed()) window.webContents.send('neutron:snapshot', desktopSnapshot()); });
  initialized = service.initialize().finally(() => { initializing = false; });
  void initialized.catch(error => service.setError(error));
  await window.loadURL(entry);
  await initialized;
  verificationMs = Math.round(performance.now() - startedAt); recordStartup();
}).catch(error => { console.error(error); app.exit(1); });
app.on('before-quit', event => { if (!quitting) { event.preventDefault(); void quit(); } });
app.on('window-all-closed', () => { void quit(); });
