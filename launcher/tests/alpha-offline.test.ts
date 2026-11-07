import { afterEach, expect, test, vi } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { download, response } from '../electron/network.cts';
import { hash } from '../electron/files.cts';
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); });
test('offline operation forbids network while reusing only intact hashed downloads', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-offline-'));
  try {
    vi.stubEnv('NEUTRON_OFFLINE', '1'); const network = vi.fn(() => { throw new Error('unexpected fetch'); }); vi.stubGlobal('fetch', network);
    const file = path.join(root, 'object'); await fs.writeFile(file, 'verified cache');
    const item = { url: 'https://libraries.minecraft.net/fixture.jar', digest: await hash(file) };
    expect(await download(item, file, new AbortController().signal)).toBe(file);
    await expect(response(item.url, new AbortController().signal)).rejects.toThrow('disabled');
    await fs.writeFile(file, 'corrupt');
    await expect(download(item, file, new AbortController().signal)).rejects.toThrow('disabled');
    expect(network).not.toHaveBeenCalled();
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
