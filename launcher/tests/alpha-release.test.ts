import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { parseAlphaRelease } from '../electron/alpha-release.cts';
import { LauncherService } from '../electron/service.cts';

const mocks = vi.hoisted(() => ({ tools: vi.fn(), runtime: vi.fn(), install: vi.fn(), verifyRuntime: vi.fn(), build: vi.fn(), reuse: vi.fn(), verify: vi.fn(), inspect: vi.fn(), matches: vi.fn(), kill: vi.fn(), context: vi.fn(), bootstrap: vi.fn() }));
vi.mock('../electron/installation-context.cts', () => ({ assertUserInstallation: mocks.context }));
vi.mock('../electron/validate-client.cts', () => ({ validateClient: mocks.bootstrap }));
vi.mock('../electron/lease.cts', () => ({ inspectProcess: mocks.inspect, processMatches: mocks.matches, commandHash: () => 'fixture-identity' }));
vi.mock('../electron/processes.cts', async original => ({ ...await original<typeof import('../electron/processes.cts')>(), killTree: mocks.kill }));
vi.mock('../electron/toolchain.cts', () => ({ installTools: mocks.tools, verifyTools: mocks.tools }));
vi.mock('../electron/minecraft.cts', () => ({ installRuntime: mocks.install, verifyRuntime: mocks.verifyRuntime }));
vi.mock('../electron/distribution.cts', () => ({ buildDistribution: mocks.build, reusableDistribution: mocks.reuse, verifyDistribution: mocks.verify, resolveMain: vi.fn(), launchArguments: vi.fn() }));
const release = { schemaVersion: 1 as const, channel: 'alpha' as const, launcherVersion: '0.2.0-alpha.1', clientCommit: 'a'.repeat(40), sourceArchiveSha256: 'b'.repeat(64) };
const runtime = { directory: 'runtimes/' + randomUUID(), manifestSha256: 'c'.repeat(64) };
const build = { id: release.clientCommit + '-' + randomUUID(), commit: release.clientCommit, date: new Date().toISOString(), sizeMiB: 1, manifestSha256: 'd'.repeat(64) };
let root: string; let service: LauncherService | undefined;
beforeEach(async () => {
  vi.resetAllMocks(); root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-alpha-'));
  mocks.tools.mockResolvedValue({ javaHome: 'managed-java', mavenHome: 'managed-maven' });
  mocks.install.mockResolvedValue(runtime); mocks.verifyRuntime.mockResolvedValue({ directory: 'verified-runtime' });
  mocks.build.mockResolvedValue(build); mocks.reuse.mockResolvedValue(null);
  mocks.verify.mockResolvedValue({ manifest: { archiveSha256: release.sourceArchiveSha256, runtime, classpath: [{ path: 'neutron-client.jar', sha256: '9'.repeat(64) }] } });
});
afterEach(async () => { vi.restoreAllMocks(); await service?.shutdown(); service = undefined; await fs.rm(root, { recursive: true, force: true }); });

test('Alpha pins reject moving refs, missing archive hashes and unrelated test claims', () => {
  expect(parseAlphaRelease(release)).toEqual(release);
  for (const patch of [{ clientCommit: 'main' }, { sourceArchiveSha256: '' }, { channel: 'stable' }, { testedClientCommit: 'e'.repeat(40) }]) expect(() => parseAlphaRelease({ ...release, ...patch })).toThrow('Invalid pinned');
  expect(parseAlphaRelease({ ...release, testedClientCommit: release.clientCommit }).testedClientCommit).toBe(release.clientCommit);
});

test('fresh installer prepares the pinned client before success and records verified receipt', async () => {
  service = new LauncherService(root, 'adapter', undefined, release); await service.initialize();
  const result = await service.prepareAlpha();
  expect(mocks.build.mock.calls[0][1]).toBe(release.clientCommit);
  expect(mocks.build.mock.calls[0].slice(-2)).toEqual([release.sourceArchiveSha256, false]);
  expect(service.snapshot().selected).toBe(build.id); expect(result.clientManifestSha256).toBe(build.manifestSha256);
  const receipt = JSON.parse(await fs.readFile(path.join(root, 'logs/installer-result.json'), 'utf8'));
  expect(receipt.clientCommit).toBe(release.clientCommit); expect(receipt.setupMs).toBeGreaterThanOrEqual(0);
});

test('already validated exact commit may omit install-time tests, while normal builds retain them', async () => {
  service = new LauncherService(root, 'adapter', undefined, { ...release, testedClientCommit: release.clientCommit }); await service.initialize();
  await service.prepareAlpha(); expect(mocks.build.mock.calls[0].at(-1)).toBe(true);
});

async function previousInstallation() {
  service = new LauncherService(root, 'adapter', undefined, release);
  const original = service.snapshot();
  const old = { ...build, id: 'e'.repeat(40) + '-' + randomUUID(), commit: 'e'.repeat(40) };
  await fs.writeFile(path.join(root, 'state.json'), JSON.stringify({ schemaVersion: 1, options: { ...original.options, gameDir: path.join(root, 'personal-worlds') }, preferences: { ...original.preferences, locale: 'en' }, builds: [old], selected: old.id, runtime }));
  await fs.mkdir(path.join(root, 'personal-worlds')); await fs.writeFile(path.join(root, 'personal-worlds/keep.dat'), 'personal data');
  await service.initialize(); return old;
}

test('network/build failure preserves selected version, settings and personal profile', async () => {
  const old = await previousInstallation(); const before = await fs.readFile(path.join(root, 'state.json'), 'utf8');
  mocks.build.mockRejectedValue(new Error('network interrupted'));
  await expect(service!.prepareAlpha()).rejects.toThrow('network interrupted');
  expect(service!.snapshot().selected).toBe(old.id); expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe(before);
  expect(await fs.readFile(path.join(root, 'personal-worlds/keep.dat'), 'utf8')).toBe('personal data');
  await expect(fs.access(path.join(root, 'logs/installer-result.json'))).rejects.toThrow();
});

test('upgrade reuses valid runtime; repeat installation is fully local and preserves options', async () => {
  await previousInstallation(); await service!.prepareAlpha();
  mocks.reuse.mockResolvedValue(build); mocks.build.mockRejectedValue(new Error('offline; Maven must not run'));
  mocks.install.mockRejectedValue(new Error('offline; runtime must not download'));
  const before = service!.snapshot().options;
  await service!.prepareAlpha(); await service!.verifyAlpha();
  expect(mocks.install).not.toHaveBeenCalled(); expect(mocks.build).toHaveBeenCalledTimes(1);
  expect(service!.snapshot().options).toEqual(before); expect(service!.snapshot().preferences.locale).toBe('en');
});

test('source archive mismatch in cached client never counts as successful offline install', async () => {
  await previousInstallation(); mocks.reuse.mockResolvedValue(build);
  mocks.verify.mockResolvedValue({ manifest: { archiveSha256: 'f'.repeat(64), runtime } });
  mocks.build.mockRejectedValue(new Error('offline; damaged cache needs repair'));
  await expect(service!.prepareAlpha()).rejects.toThrow('damaged cache needs repair');
  expect(service!.snapshot().selected).not.toBe(build.id);
});

test('cancelled preparation never activates a client returned after cancellation', async () => {
  const old = await previousInstallation();
  let finish!: (value: typeof build) => void;
  mocks.build.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const pending = expect(service!.prepareAlpha()).rejects.toThrow('cancelled');
  await vi.waitFor(() => expect(mocks.build).toHaveBeenCalledTimes(1));
  const cancelled = service!.execute({ type: 'cancel' }); finish(build);
  await cancelled; await pending; expect(service!.snapshot().selected).toBe(old.id);
});

test('installer refuses an active recovered client and exits without stopping it or deleting its lease', async () => {
  const old = await previousInstallation(); await service!.shutdown();
  const lease = JSON.stringify({ pid: 12345, buildId: old.id, executable: 'fixture-javaw.exe' });
  await fs.writeFile(path.join(root, 'client-process.json'), lease);
  mocks.inspect.mockResolvedValue({ created: 'fixture-time' }); mocks.matches.mockReturnValue(true);
  service = new LauncherService(root, 'adapter', undefined, release); await service.initialize();
  await expect(service.prepareAlpha()).rejects.toThrow('client is running');
  await service.shutdown(true); expect(mocks.kill).not.toHaveBeenCalled();
  expect(await fs.readFile(path.join(root, 'client-process.json'), 'utf8')).toBe(lease);
  service = undefined;
});

test('receipt write failure occurs before selection changes, with original state bytes retained', async () => {
  const old = await previousInstallation(); const before = await fs.readFile(path.join(root, 'state.json'), 'utf8');
  const open = fs.open.bind(fs);
  vi.spyOn(fs, 'open').mockImplementation(async (...args) => {
    if (String(args[0]).includes('installer-result.json')) throw Object.assign(new Error('receipt permission denied'), { code: 'EACCES' });
    return open(...args);
  });
  await expect(service!.prepareAlpha()).rejects.toThrow('receipt permission denied');
  expect(service!.snapshot().selected).toBe(old.id); expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe(before);
});

test('a diagnostic failure after committed setup does not report a false installer failure', async () => {
  await previousInstallation(); const flush = service!.logs.flush.bind(service!.logs); let calls = 0;
  vi.spyOn(service!.logs, 'flush').mockImplementation(async () => { if (++calls > 1) throw new Error('late diagnostic failure'); await flush(); });
  await expect(service!.prepareAlpha()).resolves.toMatchObject({ selectedBuildId: build.id });
  expect(service!.snapshot().selected).toBe(build.id); await expect(service!.shutdown(true)).resolves.toBeUndefined();
});

test.each(['{bad JSON', JSON.stringify({ schemaVersion: 99 }), JSON.stringify({ schemaVersion: 1, builds: [], selected: null, runtime: null, options: { ramMiB: -1 }, preferences: {} })])('unreadable existing state cannot be overwritten even when its recovery copy fails', async content => {
  await fs.writeFile(path.join(root, 'state.json'), content);
  vi.spyOn(fs, 'copyFile').mockRejectedValue(Object.assign(new Error('backup permission denied'), { code: 'EACCES' }));
  service = new LauncherService(root, 'adapter', undefined, release); await service.initialize();
  await expect(service.prepareAlpha()).rejects.toThrow('state is unreadable or invalid');
  expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe(content); expect(mocks.build).not.toHaveBeenCalled();
});

test('old exact cache with a failing Bootstrap stays unselected', async () => {
  const old = await previousInstallation(); mocks.reuse.mockResolvedValue(build);
  mocks.bootstrap.mockRejectedValue(new Error('old cached Bootstrap failed'));
  await expect(service!.prepareAlpha()).rejects.toThrow('old cached Bootstrap failed');
  expect(mocks.build).not.toHaveBeenCalled(); expect(service!.snapshot().selected).toBe(old.id);
});

test('elevated preparation is refused before downloads or activation', async () => {
  const old = await previousInstallation(); mocks.context.mockRejectedValue(new Error('elevated fixture'));
  await expect(service!.prepareAlpha()).rejects.toThrow('elevated fixture');
  expect(mocks.install).not.toHaveBeenCalled(); expect(mocks.build).not.toHaveBeenCalled(); expect(service!.snapshot().selected).toBe(old.id);
});

test('an intact repeated install can reuse the cache when less than 3 GiB remains', async () => {
  await previousInstallation(); mocks.reuse.mockResolvedValue(build);
  vi.spyOn(fs, 'statfs').mockResolvedValue({ bavail: 2, bsize: 1024 ** 3 } as Awaited<ReturnType<typeof fs.statfs>>);
  await expect(service!.prepareAlpha()).resolves.toMatchObject({ selectedBuildId: build.id });
  expect(mocks.build).not.toHaveBeenCalled(); expect(mocks.install).not.toHaveBeenCalled();
});
