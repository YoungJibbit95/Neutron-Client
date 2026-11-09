import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { parseLauncherRelease } from '../electron/alpha-release.cts';
import { LauncherService } from '../electron/service.cts';

const mocks = vi.hoisted(() => ({ tools: vi.fn(), runtime: vi.fn(), install: vi.fn(), verifyRuntime: vi.fn(), catalog: vi.fn(), build: vi.fn(), reuse: vi.fn(), verify: vi.fn(), inspect: vi.fn(), matches: vi.fn(), kill: vi.fn(), context: vi.fn(), bootstrap: vi.fn() }));
vi.mock('../electron/installation-context.cts', () => ({ assertUserInstallation: mocks.context }));
vi.mock('../electron/validate-client.cts', () => ({ validateClient: mocks.bootstrap }));
vi.mock('../electron/lease.cts', () => ({ inspectProcess: mocks.inspect, processMatches: mocks.matches, commandHash: () => 'fixture-identity' }));
vi.mock('../electron/processes.cts', async original => ({ ...await original<typeof import('../electron/processes.cts')>(), killTree: mocks.kill }));
vi.mock('../electron/toolchain.cts', () => ({ installTools: mocks.tools, verifyTools: mocks.tools }));
vi.mock('../electron/minecraft.cts', () => ({ installRuntime: mocks.install, verifyRuntime: mocks.verifyRuntime }));
vi.mock('../electron/distribution.cts', () => ({ installReleaseDistribution: mocks.build, reusableReleaseDistribution: mocks.reuse, verifyDistribution: mocks.verify, clientClasspath: vi.fn(), launchArguments: vi.fn() }));
vi.mock('../electron/release-catalog.cts', () => ({ listClientReleases: mocks.catalog }));
class FixtureService extends LauncherService { protected requireWindows() {} }
const release = { schemaVersion: 2 as const, channel: 'stable' as const, launcherVersion: '0.3.0', repository: 'YoungJibbit95/Neutron-Client' as const };
const client = { id: 101, tag: 'v0.2.1', version: '0.2.1', publishedAt: '2026-11-08T12:00:00Z', prerelease: false,
  jar: { id: 201, name: 'Neutron-0.2.1.jar', url: 'https://github.com/YoungJibbit95/Neutron-Client/releases/download/v0.2.1/Neutron-0.2.1.jar', sha256: '9'.repeat(64), size: 1000 },
  json: { id: 202, name: 'Neutron-0.2.1.json', url: 'https://github.com/YoungJibbit95/Neutron-Client/releases/download/v0.2.1/Neutron-0.2.1.json', sha256: '8'.repeat(64), size: 100 } };
const runtime = { directory: 'runtimes/' + randomUUID(), manifestSha256: 'c'.repeat(64) };
const build = { id: 'a'.repeat(40) + '-' + randomUUID(), commit: 'a'.repeat(40), date: new Date().toISOString(), sizeMiB: 1, manifestSha256: 'd'.repeat(64), releaseTag: client.tag, releaseId: client.id, version: client.version };
let root: string; let service: LauncherService | undefined;
beforeEach(async () => {
  vi.resetAllMocks(); root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-alpha-'));
  mocks.tools.mockResolvedValue({ javaHome: 'managed-java', toolchainId: 'temurin8u482b08' }); mocks.catalog.mockResolvedValue([client]);
  mocks.install.mockResolvedValue(runtime); mocks.verifyRuntime.mockResolvedValue({ directory: 'verified-runtime' });
  mocks.build.mockResolvedValue(build); mocks.reuse.mockResolvedValue(null);
  mocks.verify.mockResolvedValue({ manifest: { schemaVersion: 2, release: client, version: 'Neutron-' + client.version, runtime, classpath: [{ path: 'neutron-client.jar', sha256: client.jar.sha256 }], versionJson: { path: 'neutron-version.json', sha256: client.json.sha256 } } });
});
afterEach(async () => { vi.restoreAllMocks(); vi.unstubAllEnvs(); await service?.shutdown(); service = undefined; await fs.rm(root, { recursive: true, force: true }); });

test('packaged metadata fixes the repository and rejects removed source pins', () => {
  expect(parseLauncherRelease(release)).toEqual(release);
  for (const patch of [{ schemaVersion: 1 }, { repository: 'somebody/other' }, { clientCommit: 'a'.repeat(40) }, { sourceArchiveSha256: 'b'.repeat(64) }, { channel: 'alpha' }, { launcherVersion: 'main' }]) expect(() => parseLauncherRelease({ ...release, ...patch })).toThrow('Invalid launcher release');
  expect(parseLauncherRelease({ ...release, channel: 'alpha', launcherVersion: '0.3.0-alpha.1' }).channel).toBe('alpha');
});

test('fresh installer downloads the latest stable client before success and records verified receipt', async () => {
  service = new FixtureService(root, undefined, release); await service.initialize();
  const result = await service.prepareRelease();
  expect(mocks.build.mock.calls[0][1]).toEqual(client);
  expect(result).toMatchObject({ schemaVersion: 2, releaseTag: client.tag, clientJarSha256: client.jar.sha256, versionJsonSha256: client.json.sha256 });
  expect(service.snapshot().selected).toBe(build.id); expect(result.clientManifestSha256).toBe(build.manifestSha256);
  const receipt = JSON.parse(await fs.readFile(path.join(root, 'logs/installer-result.json'), 'utf8'));
  expect(receipt.clientCommit).toBe('a'.repeat(40)); expect(receipt.setupMs).toBeGreaterThanOrEqual(0);
});

async function previousInstallation() {
  service = new FixtureService(root, undefined, release);
  const original = service.snapshot();
  const old = { ...build, id: 'e'.repeat(40) + '-' + randomUUID(), commit: 'e'.repeat(40) };
  await fs.writeFile(path.join(root, 'state.json'), JSON.stringify({ schemaVersion: 1, options: { ...original.options, gameDir: path.join(root, 'personal-worlds') }, preferences: { ...original.preferences, locale: 'en' }, builds: [old], selected: old.id, runtime }));
  await fs.mkdir(path.join(root, 'personal-worlds')); await fs.writeFile(path.join(root, 'personal-worlds/keep.dat'), 'personal data');
  await service.initialize(); return old;
}

test('download failure preserves selected version, settings and personal profile', async () => {
  const old = await previousInstallation(); const before = await fs.readFile(path.join(root, 'state.json'), 'utf8');
  mocks.build.mockRejectedValue(new Error('network interrupted'));
  await expect(service!.prepareRelease()).rejects.toThrow('network interrupted');
  expect(service!.snapshot().selected).toBe(old.id); expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe(before);
  expect(await fs.readFile(path.join(root, 'personal-worlds/keep.dat'), 'utf8')).toBe('personal data');
  await expect(fs.access(path.join(root, 'logs/installer-result.json'))).rejects.toThrow();
});

test('upgrade reuses valid runtime; repeat installation is fully local and preserves options', async () => {
  await previousInstallation(); await service!.prepareRelease();
  mocks.reuse.mockResolvedValue(build); mocks.build.mockRejectedValue(new Error('offline; Maven must not run'));
  mocks.install.mockRejectedValue(new Error('offline; runtime must not download'));
  vi.stubEnv('NEUTRON_OFFLINE', '1'); mocks.catalog.mockRejectedValue(new Error('network must not run'));
  const before = service!.snapshot().options;
  await service!.prepareRelease(); await service!.verifyRelease();
  expect(mocks.install).not.toHaveBeenCalled(); expect(mocks.build).toHaveBeenCalledTimes(1);
  expect(service!.snapshot().options).toEqual(before); expect(service!.snapshot().preferences.locale).toBe('en');
});

test('corrupt JSON in a reusable client never counts as successful installation', async () => {
  const old = await previousInstallation(); mocks.reuse.mockResolvedValue(build);
  mocks.verify.mockRejectedValue(new Error('version JSON corrupt'));
  await expect(service!.prepareRelease()).rejects.toThrow('version JSON corrupt');
  expect(service!.snapshot().selected).toBe(old.id);
});

test('cancelled preparation never activates a client returned after cancellation', async () => {
  const old = await previousInstallation();
  let finish!: (value: typeof build) => void;
  mocks.build.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const pending = expect(service!.prepareRelease()).rejects.toThrow('cancelled');
  await vi.waitFor(() => expect(mocks.build).toHaveBeenCalledTimes(1));
  const cancelled = service!.execute({ type: 'cancel' }); finish(build);
  await cancelled; await pending; expect(service!.snapshot().selected).toBe(old.id);
});

test('installer refuses an active recovered client and exits without stopping it or deleting its lease', async () => {
  const old = await previousInstallation(); await service!.shutdown();
  const lease = JSON.stringify({ pid: 12345, buildId: old.id, executable: 'fixture-javaw.exe' });
  await fs.writeFile(path.join(root, 'client-process.json'), lease);
  mocks.inspect.mockResolvedValue({ created: 'fixture-time' }); mocks.matches.mockReturnValue(true);
  service = new FixtureService(root, undefined, release); await service.initialize();
  await expect(service.prepareRelease()).rejects.toThrow('client is running');
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
  await expect(service!.prepareRelease()).rejects.toThrow('receipt permission denied');
  expect(service!.snapshot().selected).toBe(old.id); expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe(before);
});

test('a diagnostic failure after committed setup does not report a false installer failure', async () => {
  await previousInstallation(); const flush = service!.logs.flush.bind(service!.logs); let calls = 0;
  vi.spyOn(service!.logs, 'flush').mockImplementation(async () => { if (++calls > 1) throw new Error('late diagnostic failure'); await flush(); });
  await expect(service!.prepareRelease()).resolves.toMatchObject({ selectedBuildId: build.id });
  expect(service!.snapshot().selected).toBe(build.id); await expect(service!.shutdown(true)).resolves.toBeUndefined();
});

test.each(['{bad JSON', JSON.stringify({ schemaVersion: 99 }), JSON.stringify({ schemaVersion: 1, builds: [], selected: null, runtime: null, options: { ramMiB: -1 }, preferences: {} })])('unreadable existing state cannot be overwritten even when its recovery copy fails', async content => {
  await fs.writeFile(path.join(root, 'state.json'), content);
  vi.spyOn(fs, 'copyFile').mockRejectedValue(Object.assign(new Error('backup permission denied'), { code: 'EACCES' }));
  service = new FixtureService(root, undefined, release); await service.initialize();
  await expect(service.prepareRelease()).rejects.toThrow('state is unreadable or invalid');
  expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe(content); expect(mocks.build).not.toHaveBeenCalled();
});

test('old exact cache with a failing Bootstrap stays unselected', async () => {
  const old = await previousInstallation(); mocks.reuse.mockResolvedValue(build);
  mocks.bootstrap.mockRejectedValue(new Error('old cached Bootstrap failed'));
  await expect(service!.prepareRelease()).rejects.toThrow('old cached Bootstrap failed');
  expect(mocks.build).not.toHaveBeenCalled(); expect(service!.snapshot().selected).toBe(old.id);
});

test('elevated preparation is refused before downloads or activation', async () => {
  const old = await previousInstallation(); mocks.context.mockRejectedValue(new Error('elevated fixture'));
  await expect(service!.prepareRelease()).rejects.toThrow('elevated fixture');
  expect(mocks.install).not.toHaveBeenCalled(); expect(mocks.build).not.toHaveBeenCalled(); expect(service!.snapshot().selected).toBe(old.id);
});

test('an intact repeated install can reuse the cache when less than 3 GiB remains', async () => {
  await previousInstallation(); mocks.reuse.mockResolvedValue(build);
  vi.spyOn(fs, 'statfs').mockResolvedValue({ bavail: 2, bsize: 1024 ** 3 } as Awaited<ReturnType<typeof fs.statfs>>);
  await expect(service!.prepareRelease()).resolves.toMatchObject({ selectedBuildId: build.id });
  expect(mocks.build).not.toHaveBeenCalled(); expect(mocks.install).not.toHaveBeenCalled();
});

test('latest stable is preferred while newer prereleases remain selectable', async () => {
  const prerelease = { ...client, id: 102, tag: 'v0.3.0-beta.1', version: '0.3.0-beta.1', prerelease: true };
  mocks.catalog.mockResolvedValue([prerelease, client]);
  service = new FixtureService(root, undefined, release); await service.initialize(); await service.prepareRelease();
  expect(mocks.build.mock.calls[0][1]).toEqual(client);
  expect(service.snapshot()).toMatchObject({ latestRelease: client.tag, releases: [prerelease, client] });
});

test('withdrawn release and renderer asset injection cannot initiate a download', async () => {
  await previousInstallation(); await service!.execute({ type: 'install', tag: 'v0.1.0', play: false }); await service!.idle();
  expect(service!.snapshot().error).toContain('no longer available'); expect(mocks.build).not.toHaveBeenCalled();
  await expect(service!.execute({ type: 'install', tag: client.tag, play: false, url: 'https://attacker.invalid/client.jar' })).rejects.toThrow('Unknown command field');
  await expect(service!.execute({ type: 'build', play: false })).rejects.toThrow('Unknown launcher command');
});

test('background refresh retains installed versions and last catalog on connection loss', async () => {
  const old = await previousInstallation(); await service!.refreshReleases(); const checked = service!.snapshot().lastChecked;
  mocks.catalog.mockRejectedValue(new Error('rate limit')); await service!.refreshReleases();
  expect(service!.snapshot()).toMatchObject({ online: false, status: 'offline', selected: old.id, releases: [client], lastChecked: checked });
  expect(mocks.build).not.toHaveBeenCalled();
});

test('ordinary reinstall falls back to verified installed assets when GitHub is unreachable', async () => {
  await previousInstallation(); mocks.reuse.mockResolvedValue(build);
  mocks.catalog.mockRejectedValue(new Error('network unavailable'));
  await expect(service!.prepareRelease()).resolves.toMatchObject({ releaseTag: client.tag });
  expect(mocks.build).not.toHaveBeenCalled(); expect(mocks.install).not.toHaveBeenCalled();
  expect(service!.snapshot().status).toBe('offline');
});

test('explicit download does not silently fall back when GitHub is unreachable', async () => {
  const old = await previousInstallation(); mocks.catalog.mockRejectedValue(new Error('network unavailable'));
  await service!.execute({ type: 'install', tag: client.tag, play: false }); await service!.idle();
  expect(service!.snapshot().error).toContain('network unavailable');
  expect(mocks.verify).not.toHaveBeenCalled(); expect(service!.snapshot().selected).toBe(old.id);
});

test('settings cannot persist an uncommitted selection while activation fails', async () => {
  const old = await previousInstallation(); const before = await fs.readFile(path.join(root, 'state.json'), 'utf8');
  const open = fs.open.bind(fs); let rejectWrite!: (error: Error) => void;
  vi.spyOn(fs, 'open').mockImplementation(async (...args) => {
    if (String(args[0]).includes('state.json.')) return new Promise((_resolve, reject) => { rejectWrite = reject; });
    return open(...args);
  });
  const installation = expect(service!.prepareRelease()).rejects.toThrow('activation write denied');
  await vi.waitFor(() => expect(rejectWrite).toBeTypeOf('function'));
  await expect(service!.execute({ type: 'save-preferences', patch: { locale: 'de' } })).rejects.toThrow('activation');
  await expect(service!.execute({ type: 'save-options', patch: { ramMiB: 2048 } })).rejects.toThrow('activation');
  rejectWrite(new Error('activation write denied')); await installation;
  expect(service!.snapshot().selected).toBe(old.id);
  expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe(before);
});
