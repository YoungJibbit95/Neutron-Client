import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import yazl from 'yazl';
import { prepareSource } from '../electron/distribution.cts';
import { atomicJson, hash } from '../electron/files.cts';
const calls = vi.hoisted(() => ({ response: vi.fn(), adapter: vi.fn() }));
vi.mock('../electron/network.cts', () => ({ response: calls.response, json: vi.fn() }));
vi.mock('../electron/source-adapter.cts', () => ({ adaptSource: calls.adapter }));
let root: string;
const commit = 'a'.repeat(40);
beforeEach(async () => { vi.resetAllMocks(); root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-alpha-pin-')); calls.adapter.mockResolvedValue({ files: [] }); });
afterEach(async () => { await fs.rm(root, { recursive: true, force: true }); });
async function fixture() {
  const cache = path.join(root, 'cache/source-' + commit + '.zip'); await fs.mkdir(path.dirname(cache), { recursive: true });
  const adapter = path.join(root, 'adapter'); await fs.mkdir(adapter); await fs.writeFile(path.join(adapter, 'contract.json'), '{}');
  const zip = new yazl.ZipFile(); zip.addBuffer(Buffer.from('pinned source'), 'Neutron-Client-' + commit + '/pom.xml');
  const done = pipeline(zip.outputStream, createWriteStream(cache)); zip.end(); await done;
  const sha256 = await hash(cache); await atomicJson(cache + '.json', { commit, sha256 }); return { cache, adapter, sha256 };
}
test('pinned complete source cache prepares offline without any network', async () => {
  const item = await fixture(); calls.response.mockRejectedValue(new Error('offline'));
  const result = await prepareSource(root, commit, item.adapter, new AbortController().signal, item.sha256);
  expect(result.snapshot.archiveSha256).toBe(item.sha256); expect(calls.response).not.toHaveBeenCalled();
});
test('unexpected downloaded archive is rejected before adaptation and never replaces a valid receipt', async () => {
  const item = await fixture(); const before = await fs.readFile(item.cache + '.json', 'utf8');
  calls.response.mockResolvedValue(new Response('wrong download bytes'));
  await expect(prepareSource(root, commit, item.adapter, new AbortController().signal, 'f'.repeat(64))).rejects.toThrow('integrity failed');
  expect(calls.adapter).not.toHaveBeenCalled(); expect(await fs.readFile(item.cache + '.json', 'utf8')).toBe(before);
});
test('interrupted source stream cannot be accepted or overwrite selected state', async () => {
  const item = await fixture(); const registry = path.join(root, 'state.json'); await fs.writeFile(registry, 'previous playable state');
  calls.response.mockResolvedValue(new Response(new ReadableStream({ start(controller) { controller.enqueue(new Uint8Array([1, 2])); controller.error(new Error('network interrupted')); } })));
  await expect(prepareSource(root, commit, item.adapter, new AbortController().signal, 'f'.repeat(64))).rejects.toThrow('network interrupted');
  expect(calls.adapter).not.toHaveBeenCalled(); expect(await fs.readFile(registry, 'utf8')).toBe('previous playable state');
});
