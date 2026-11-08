import { afterEach, beforeEach, expect, test } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { readStorageLocation, requireStorageCapacity, resolveStorageRoot, storageLocationName, validateStoragePath } from '../electron/storage-location.cts';

let sandbox: string; let control: string; let copied: string; let install: string;
const id = 'a'.repeat(40) + '-' + randomUUID();
function state(gameDir: string) { return { schemaVersion: 1, options: { ramMiB: 2048, gameDir, java: 'managed', javaPath: '', width: 1280, height: 720, fullscreen: false, jvmArgs: '' }, preferences: { theme: 'ocean', locale: 'de', scale: 100, effects: 70, reducedMotion: false }, builds: [{ id, commit: 'a'.repeat(40), manifestSha256: 'b'.repeat(64), sizeMiB: 1, date: new Date().toISOString() }], selected: id, runtime: null }; }
beforeEach(async () => {
  sandbox = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-location-'));
  control = path.join(sandbox, 'Roaming', 'Neutron Launcher'); copied = path.join(sandbox, 'Other drive', 'Neutron Daten Ö'); install = path.join(sandbox, 'Programs', 'Neutron Launcher');
  await fs.mkdir(control, { recursive: true }); await fs.mkdir(copied, { recursive: true });
});
afterEach(async () => { await fs.rm(sandbox, { recursive: true, force: true }); });
async function profile(root: string) {
  const value = state(path.join(sandbox, 'separate-game'));
  await fs.writeFile(path.join(root, 'state.json'), JSON.stringify(value));
  await fs.mkdir(path.join(root, 'versions', id), { recursive: true }); await fs.writeFile(path.join(root, 'versions', id, 'launch-manifest.json'), '{}');
  return value;
}
async function point(value: string) { await fs.writeFile(path.join(control, storageLocationName), value, 'utf16le'); }
test('normal start and reinstall resolve the saved drive without environment overrides', async () => {
  const original = await profile(control); await fs.cp(control, copied, { recursive: true }); await point(copied);
  const pointer = await fs.readFile(path.join(control, storageLocationName)); const source = await fs.readFile(path.join(control, 'state.json'));
  for (let i = 0; i < 2; i++) expect(resolveStorageRoot({ appData: path.dirname(control), installation: install, packaged: true })).toBe(copied);
  expect(JSON.parse(await fs.readFile(path.join(copied, 'state.json'), 'utf8')).options.gameDir).toBe(original.options.gameDir);
  expect(await fs.readFile(path.join(control, 'state.json'))).toEqual(source); expect(await fs.readFile(path.join(control, storageLocationName))).toEqual(pointer);
});
test('legacy users retain their existing root until a successful explicit selection', async () => {
  await profile(control); expect(resolveStorageRoot({ appData: path.dirname(control), packaged: true })).toBe(control);
  await expect(fs.access(path.join(control, storageLocationName))).rejects.toThrow();
});
test('an explicit fixture override neither reads nor rewrites the real saved selector', async () => {
  await point('broken selector'); const before = await fs.readFile(path.join(control, storageLocationName));
  expect(resolveStorageRoot({ appData: path.dirname(control), installation: install, packaged: true, override: copied })).toBe(copied);
  expect(() => resolveStorageRoot({ appData: path.dirname(control), packaged: true, override: '' })).toThrow('absolute local');
  expect(await fs.readFile(path.join(control, storageLocationName))).toEqual(before);
});
test('an unavailable saved drive fails explicitly without reverting to the legacy profile', async () => {
  await profile(control); const unused = 'ZYXWVUTSRQPONMLKJIHGED'.split('').find(letter => !require('node:fs').existsSync(letter + ':\\')); expect(unused).toBeTruthy();
  await point(unused + ':\\Neutron Data'); const before = await fs.readFile(path.join(control, 'state.json'));
  expect(() => readStorageLocation(control, install)).toThrow('drive is unavailable'); expect(await fs.readFile(path.join(control, 'state.json'))).toEqual(before);
});
test.each(['empty', 'invalid state', 'missing selected version'])('an %s target never becomes an empty replacement profile', async kind => {
  await profile(control); await point(copied); const before = await fs.readFile(path.join(control, storageLocationName));
  if (kind !== 'empty') await fs.writeFile(path.join(copied, 'state.json'), kind === 'invalid state' ? '{"schemaVersion":99}' : JSON.stringify(state('F:\\Separate Game')));
  expect(() => readStorageLocation(control, install)).toThrow(); expect(await fs.readFile(path.join(control, storageLocationName))).toEqual(before);
  expect(JSON.parse(await fs.readFile(path.join(control, 'state.json'), 'utf8')).selected).toBe(id);
});
test.each(['C:\\', 'F:\\Apps\\Launcher\\data', 'F:\\Apps', 'F:\\Apps\\Launcher\\..notes', '\\\\server\\share', 'relative', 'F:\\bad:stream', 'F:\\bad\nname'])('unsafe storage location is rejected: %s', value => {
  expect(() => validateStoragePath(value, 'F:\\Apps\\Launcher')).toThrow();
});
test('the capacity error names the actual data folder and free space', () => {
  expect(() => requireStorageCapacity('C:\\Neutron Data', 2.77 * 1024 ** 3)).toThrow(/C:\\Neutron Data.*2\.77 GiB.*installation folder is separate/);
  expect(() => requireStorageCapacity('F:\\Neutron Data', 122 * 1024 ** 3)).not.toThrow();
});
test('linked saved storage is rejected', async () => {
  await profile(copied); const link = path.join(sandbox, 'linked'); await fs.symlink(copied, link, 'junction'); await point(link);
  expect(() => readStorageLocation(control, install)).toThrow('Linked launcher storage');
});
