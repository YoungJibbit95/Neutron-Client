import { contextBridge, ipcRenderer } from 'electron';
import type { Bridge, Command, Snapshot } from './contracts.cjs';
const bridge: Bridge = {
  ready: () => ipcRenderer.send('neutron:renderer-ready'),
  mode: 'live', snapshot: () => ipcRenderer.invoke('neutron:snapshot'),
  command: (command: Command) => ipcRenderer.invoke('neutron:command', command),
  subscribe: listener => { const handler = (_event: Electron.IpcRendererEvent, snapshot: Snapshot) => listener(snapshot); ipcRenderer.on('neutron:snapshot', handler); return () => ipcRenderer.removeListener('neutron:snapshot', handler); },
  onClose: listener => { const handler = () => listener(); ipcRenderer.on('neutron:close-request', handler); return () => ipcRenderer.removeListener('neutron:close-request', handler); },
};
contextBridge.exposeInMainWorld('neutron', Object.freeze(bridge));
