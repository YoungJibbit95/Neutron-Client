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
  const javaHome = 'immutable/jdk'; const files = [];
  for (const name of ['bin/java.exe', 'bin/javaw.exe', 'jre/lib/rt.jar', ...Array.from({ length: 7 }, (_, i) => i + '.fixture')]) {
    const relative = javaHome + '/' + name; const file = path.join(root, 'tools', relative); await fs.mkdir(path.dirname(file), { recursive: true }); await fs.writeFile(file, 'retained fixture ' + name); files.push({ path: relative, sha256: await hash(file) });
  }
  const manifest = { toolchainId: id, javaHome, mavenHome: 'immutable/maven', files };
  await atomicJson(path.join(root, 'tools/manifest.json'), manifest); return manifest;
}
test.each([undefined, 'jdk8u504-maven3.9.16'])('an unsupported toolchain %s requires setup without deleting files or state', async id => {
  const original = await fixture(id); await fs.writeFile(path.join(root, 'state.json'), 'retained settings and versions');
  await expect(verifyTools(root)).rejects.toThrow('toolchain update required');
  expect(JSON.parse(await fs.readFile(path.join(root, 'tools/manifest.json'), 'utf8'))).toEqual(JSON.parse(JSON.stringify(original)));
  expect(await fs.readFile(path.join(root, 'tools/immutable/jdk/0.fixture'), 'utf8')).toBe('retained fixture 0.fixture');
  expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe('retained settings and versions');
});
test('the managed Java runtime rehashes every recorded Java file before reuse', async () => {
  await fixture(toolchainId); expect((await verifyTools(root)).toolchainId).toBe(toolchainId);
  await fs.writeFile(path.join(root, 'tools/immutable/jdk/6.fixture'), 'changed');
  await expect(verifyTools(root)).rejects.toThrow('integrity check failed');
});
test('the previous exactly pinned JDK is reusable without Maven files or a manifest rewrite', async () => {
  const original = await fixture('temurin8u482b08-maven3.9.15'); original.files.push({ path: 'immutable/maven/deleted.jar', sha256: 'f'.repeat(64) });
  await atomicJson(path.join(root, 'tools/manifest.json'), original); const before = await fs.readFile(path.join(root, 'tools/manifest.json'), 'utf8');
  const actual = await verifyTools(root); expect(actual.toolchainId).toBe(toolchainId); expect(actual).not.toHaveProperty('mavenHome'); expect(actual.files).toHaveLength(10);
  expect(await fs.readFile(path.join(root, 'tools/manifest.json'), 'utf8')).toBe(before);
});
test('a manifest missing Java launch binaries is rejected even when unrelated hashes are valid', async () => {
  const original = await fixture(toolchainId); original.files = original.files.filter(item => !item.path.endsWith('javaw.exe'));
  await atomicJson(path.join(root, 'tools/manifest.json'), original);
  await expect(verifyTools(root)).rejects.toThrow('incomplete');
});
