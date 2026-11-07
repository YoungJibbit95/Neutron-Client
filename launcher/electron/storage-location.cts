import fs from 'node:fs';
import path from 'node:path';
import { parseStoredState } from './stored-state.cjs';

export const storageLocationName = 'data-directory.txt';
export const minimumStorageBytes = 3 * 1024 ** 3;

// The small control file stays in Roaming. The selected storage may be on a
// different drive; it is never inferred again from free space on later starts.
export function validateStoragePath(value: string, installation?: string): string {
  if (!value || value.length > 900 || /[\x00-\x1f<>"|?*]/.test(value) || !/^[a-z]:[\\/]/i.test(value) || value.slice(2).includes(':')) {
    throw new Error('Choose an absolute local data folder, for example F:\\Neutron Data.');
  }
  const root = path.win32.normalize(value).replace(/\\$/, '');
  if (root === path.win32.parse(root).root || root.split(/[\\/]/).some(part => /[. ]$/.test(part))) {
    throw new Error('Choose a dedicated data folder, not a drive root.');
  }
  if (installation) {
    const contains = (a: string, b: string) => { const relative = path.win32.relative(a, b); return !relative || (relative !== '..' && !relative.startsWith('..\\') && !path.win32.isAbsolute(relative)); };
    if (contains(installation, root) || contains(root, installation)) throw new Error('The data folder must be separate from the launcher installation folder.');
  }
  return root;
}

function noLinks(target: string) {
  for (let current = target;; current = path.dirname(current)) {
    try { if (fs.lstatSync(current).isSymbolicLink()) throw new Error('Linked launcher storage is not allowed: ' + current); }
    catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
    if (current === path.dirname(current)) break;
  }
}

export function readStorageLocation(control: string, installation?: string): string | undefined {
  const file = path.join(control, storageLocationName); noLinks(file);
  let bytes: Buffer;
  try { const stat = fs.statSync(file); if (!stat.isFile() || stat.size < 2 || stat.size > 2048 || stat.size % 2) throw new Error('Invalid saved launcher data location. Rerun setup and select the existing data folder.'); bytes = fs.readFileSync(file); }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined; throw error; }
  const value = bytes.toString('utf16le').replace(/^\uFEFF/, '').replace(/\r?\n$/, '');
  const root = validateStoragePath(value, installation); noLinks(root);
  if (!fs.existsSync(path.parse(root).root)) throw new Error('The saved launcher data drive is unavailable: ' + root + '. Reconnect that drive or select the existing data folder in setup. No fallback folder was used.');
  if (!fs.existsSync(path.join(root, 'state.json'))) throw new Error('The saved launcher data folder is missing or incomplete: ' + root + '. Restore the existing folder or rerun setup; no empty replacement profile was created.');
  const stateFile = path.join(root, 'state.json'); noLinks(stateFile);
  let state: ReturnType<typeof parseStoredState>;
  try { state = parseStoredState(JSON.parse(fs.readFileSync(stateFile, 'utf8'))); }
  catch { throw new Error('Saved launcher state is invalid in ' + root + '. Restore the complete data folder; no replacement profile was created.'); }
  if (state.selected) {
    const manifest = path.join(root, 'versions', state.selected, 'launch-manifest.json'); noLinks(manifest);
    if (!fs.existsSync(manifest)) throw new Error('The selected client is missing from the saved data folder: ' + root + '. Restore the complete folder; no empty replacement profile was created.');
  }
  if (state.runtime) {
    const manifest = path.join(root, state.runtime.directory, 'runtime-manifest.json'); noLinks(manifest);
    if (!fs.existsSync(manifest)) throw new Error('The saved runtime is missing from the data folder: ' + root + '. Restore the complete folder.');
  }
  return root;
}

export function resolveStorageRoot(options: { appData: string; installation?: string; packaged: boolean; override?: string }): string {
  // An explicit override belongs to this process only. It must never mutate the
  // real user's saved selector, including when a fixture override is invalid.
  if (options.override !== undefined) {
    const root = validateStoragePath(options.override, options.installation); noLinks(root); return root;
  }
  if (!options.packaged) throw new Error('Development requires an absolute NEUTRON_DATA_ROOT on the chosen data drive.');
  const control = path.join(options.appData, 'Neutron Launcher');
  return readStorageLocation(control, options.installation) ?? control;
}

export function requireStorageCapacity(root: string, available: number) {
  if (!Number.isFinite(available) || available < minimumStorageBytes) {
    const free = Number.isFinite(available) ? (available / 1024 ** 3).toFixed(2) + ' GiB' : 'unknown';
    throw new Error('At least 3 GiB free space is required to install or rebuild Neutron files. Data folder: ' + root + '. Available: ' + free + '. Select a data folder on a drive with enough space in setup; the launcher installation folder is separate. Existing data is retained.');
  }
}
