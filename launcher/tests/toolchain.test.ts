import { afterEach, beforeEach, expect, test } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { atomicJson, hash } from '../electron/files.cts';
import { toolchainId, verifyTools } from '../electron/toolchain.cts';

let root: string;
beforeEach(async () => { root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-tools-')); });
afterEach(async () => { await fs.rm(root, { recursive: true, force: true }); });

async function fixture(id?: string) {
  const directory = path.join(root, 'tools/immutable'); await fs.mkdir(directory, { recursive: true });
  const files = [];
  for (let i = 0; i < 10; i++) {
    const file = path.join(directory, i + '.fixture'); await fs.writeFile(file, 'retained fixture ' + i);
    files.push({ path: 'immutable/' + i + '.fixture', sha256: await hash(file) });
  }
  const manifest = { toolchainId: id, javaHome: 'immutable/jdk', mavenHome: 'immutable/maven', files };
  await atomicJson(path.join(root, 'tools/manifest.json'), manifest); return manifest;
}

test.each([undefined, 'jdk8u504-maven3.9.16'])('an old toolchain %s requires setup without deleting immutable files or saved state', async id => {
  const original = await fixture(id); await fs.writeFile(path.join(root, 'state.json'), 'retained settings and versions');
  await expect(verifyTools(root)).rejects.toThrow('toolchain update required');
  expect(JSON.parse(await fs.readFile(path.join(root, 'tools/manifest.json'), 'utf8'))).toEqual(JSON.parse(JSON.stringify(original)));
  expect(await fs.readFile(path.join(root, 'tools/immutable/0.fixture'), 'utf8')).toBe('retained fixture 0');
  expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe('retained settings and versions');
});

test('the current bundle still rehashes every recorded file before reuse', async () => {
  await fixture(toolchainId);
  expect((await verifyTools(root)).toolchainId).toBe(toolchainId);
  await fs.writeFile(path.join(root, 'tools/immutable/9.fixture'), 'changed');
  await expect(verifyTools(root)).rejects.toThrow('integrity check failed');
});
