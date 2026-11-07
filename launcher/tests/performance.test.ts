import { expect, test } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { mapLimit } from '../electron/parallel.cts';
import { adapterFingerprint, reusableDistribution } from '../electron/distribution.cts';
import { atomicJson, hash } from '../electron/files.cts';

test('bounded work preserves order and waits for active readers before reporting failure', async () => {
  let active = 0; let peak = 0;
  const result = await mapLimit([3, 2, 1, 0], 2, async value => { active++; peak = Math.max(peak, active); await new Promise(done => setTimeout(done, value)); active--; return value * 2; });
  expect(result).toEqual([6, 4, 2, 0]); expect(peak).toBe(2);
  let siblingDone = false;
  await expect(mapLimit([0, 1, 2], 2, async value => { if (value === 0) throw new Error('bad digest'); await new Promise(done => setTimeout(done, 10)); siblingDone = true; })).rejects.toThrow('bad digest');
  expect(siblingDone).toBe(true); expect(active).toBe(0);
});

test('warm builds reuse only matching, fully verified commit/adapter/runtime bytes', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-reuse-'));
  try {
    const commit = 'f'.repeat(40); const id = commit + '-' + randomUUID(); const folder = path.join(root, 'versions', id); const adapter = path.join(root, 'adapter');
    await fs.mkdir(folder, { recursive: true }); await fs.mkdir(adapter); await fs.writeFile(path.join(adapter, 'pom.xml'), 'reviewed adapter');
    const jar = path.join(folder, 'client.jar'); await fs.writeFile(jar, 'verified bytes');
    const runtime = { directory: 'runtimes/' + randomUUID(), manifestSha256: 'a'.repeat(64) };
    const manifest = { schemaVersion: 1, entryPoint: 'me.neutron.launcher.ClientBootstrap', version: 'fixture', commit, runtime, adapterSha256: await adapterFingerprint(adapter), classpath: [{ path: 'client.jar', sha256: await hash(jar) }] };
    await atomicJson(path.join(folder, 'launch-manifest.json'), manifest);
    const build = { id, commit, date: new Date().toISOString(), sizeMiB: 1, manifestSha256: await hash(path.join(folder, 'launch-manifest.json')) };
    const signal = new AbortController().signal;
    expect(await reusableDistribution(root, [build], commit, adapter, runtime, signal)).toEqual(build);
    expect(await reusableDistribution(root, [build], 'e'.repeat(40), adapter, runtime, signal)).toBeNull();
    expect(await reusableDistribution(root, [build], commit, adapter, { ...runtime, manifestSha256: 'b'.repeat(64) }, signal)).toBeNull();
    await fs.writeFile(path.join(adapter, 'pom.xml'), 'changed adapter'); expect(await reusableDistribution(root, [build], commit, adapter, runtime, signal)).toBeNull();
    await fs.writeFile(path.join(adapter, 'pom.xml'), 'reviewed adapter'); await fs.writeFile(jar, 'corrupt');
    expect(await reusableDistribution(root, [build], commit, adapter, runtime, signal)).toBeNull();
    const stopped = new AbortController(); stopped.abort(new Error('cancelled'));
    await expect(reusableDistribution(root, [build], commit, adapter, runtime, stopped.signal)).rejects.toThrow('cancelled');
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
