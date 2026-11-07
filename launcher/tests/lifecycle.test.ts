import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import type { ChildProcess } from 'node:child_process';
import { LauncherService, type PreparedClient } from '../electron/service.cts';
import type { BuildRecord } from '../electron/contracts.cts';
import { atomicJson, owned } from '../electron/files.cts';
import { childEnvironment, ProcessGroup } from '../electron/processes.cts';
import * as processes from '../electron/processes.cts';

function gate<T = void>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
// Only the preparation/lease adapters are replaced. Reservation, asynchronous
// mkdir, spawn handshake, stop, exit and shutdown are the production lifecycle.
class FixtureService extends LauncherService {
  prepareGate = gate(); claimGate = gate(); claimed = gate<ChildProcess>(); holdPrepare = false; holdClaim = false; claimCount = 0;
  plan: PreparedClient;
  failTermination = false; terminationAttempts = 0; taskStarted = gate<ChildProcess>();
  constructor(root: string) { super(root, root); this.plan = { executable: process.execPath, args: ['-e', 'setInterval(()=>{},1000)'], cwd: root, temporary: root, env: childEnvironment(root, root) }; }
  protected override async prepareClient(): Promise<PreparedClient> { if (this.holdPrepare) await this.prepareGate.promise; return this.plan; }
  protected override async claimClient(child: ChildProcess, record: BuildRecord) {
    this.claimCount++; await atomicJson(path.join(this.root, 'client-process.json'), { pid: child.pid, buildId: record.id }); this.claimed.resolve(child);
    if (this.holdClaim) await this.claimGate.promise;
  }
  protected override createProcessGroup() {
    return new ProcessGroup({ terminate: async child => { this.terminationAttempts++; if (this.failTermination) throw new Error('termination denied fixture'); await processes.killTree(child); }, onStopError: error => this.setError('Failed to stop; owned process still protected: ' + String(error)) });
  }
  beginBackgroundHelper() {
    this.startTask(async (signal, group) => {
      const handle = processes.startProcess(this.plan.executable, this.plan.args, { cwd: this.plan.cwd, env: this.plan.env, signal, group, line: () => {} });
      this.taskStarted.resolve(await handle.started); await handle.exited;
    });
  }
  release() { this.failTermination = false; this.prepareGate.resolve(); this.claimGate.resolve(); }
}
let root: string; const services: FixtureService[] = []; const releases: (() => void)[] = [];
beforeEach(async () => { root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-lifecycle-')); });
afterEach(async () => {
  for (const release of releases.splice(0)) release(); for (const service of services) service.release();
  vi.restoreAllMocks(); vi.unstubAllGlobals();
  for (const service of services.splice(0)) await service.shutdown();
  await owned(root); await fs.rm(root, { recursive: true, force: true });
});
async function fixture() {
  const service = new FixtureService(root); services.push(service); const initial = service.snapshot();
  const commit = 'e'.repeat(40); const id = commit + '-' + randomUUID();
  await atomicJson(path.join(root, 'state.json'), { schemaVersion: 1, options: initial.options, preferences: initial.preferences, builds: [{ id, commit, date: new Date().toISOString(), sizeMiB: 1, manifestSha256: 'f'.repeat(64) }], selected: id, runtime: null });
  await service.initialize(); await service.logs.flush(); return { service, id, commit };
}

describe.skipIf(process.platform !== 'win32')('client lifecycle regressions — only own Node helpers, no Electron/Minecraft', () => {
  test('reservation rejects a second launch and immediate stop cancels delayed preparation before spawn', async () => {
    const { service, id } = await fixture(); service.holdPrepare = true;
    const spawn = vi.spyOn(processes, 'startProcess');
    const launching = service.execute({ type: 'launch' });
    expect(service.snapshot()).toMatchObject({ runningBuild: id, clientStarting: true, status: 'prepare' });
    await expect(service.execute({ type: 'launch' })).rejects.toThrow('already');
    const stopped = service.execute({ type: 'stop' }); service.prepareGate.resolve(); await Promise.all([launching, stopped]);
    expect(spawn).not.toHaveBeenCalled(); expect(service.claimCount).toBe(0); expect(service.snapshot().runningBuild).toBeNull(); expect(service.snapshot().clientStarting).toBe(false);
  }, 15000);
  test('shutdown waits for a start held inside mkdir, prevents later spawning and rejects a second launch', async () => {
    const { service } = await fixture(); const directory = gate<string | undefined>(); const entered = gate(); releases.push(() => directory.resolve(undefined));
    vi.spyOn(fs, 'mkdir').mockImplementationOnce(directoryPath => { expect(path.resolve(String(directoryPath))).toBe(root); entered.resolve(); return directory.promise as ReturnType<typeof fs.mkdir>; });
    const launching = service.execute({ type: 'launch' }); await entered.promise;
    let complete = false; const shutdown = service.shutdown().then(() => { complete = true; });
    await expect(service.execute({ type: 'launch' })).rejects.toThrow('closing'); expect(complete).toBe(false);
    directory.resolve(undefined); await Promise.all([launching, shutdown]);
    expect(complete).toBe(true); expect(service.claimCount).toBe(0); expect(service.snapshot().runningBuild).toBeNull();
  }, 15000);
  test('shutdown stops the actual helper while awaiting its delayed lease and removes that lease afterward', async () => {
    const { service } = await fixture(); service.holdClaim = true;
    const launching = service.execute({ type: 'launch' }); const child = await service.claimed.promise;
    expect(service.snapshot().clientStarting).toBe(true); const closed = once(child, 'close');
    let complete = false; const shutdown = service.shutdown().then(() => { complete = true; }); await closed;
    expect(complete).toBe(false); service.claimGate.resolve(); await Promise.all([launching, shutdown]);
    expect(service.snapshot().runningBuild).toBeNull(); await expect(fs.access(path.join(root, 'client-process.json'))).rejects.toMatchObject({ code: 'ENOENT' });
  }, 15000);
  test('spawn failure clears reservation and lease before rejecting, allowing a subsequent helper launch/stop', async () => {
    const { service } = await fixture(); service.plan.executable = path.join(root, 'missing-helper.exe');
    await expect(service.execute({ type: 'launch' })).rejects.toThrow();
    expect(service.snapshot().runningBuild).toBeNull(); expect(service.snapshot().clientStarting).toBe(false); expect(service.claimCount).toBe(0);
    await expect(fs.access(path.join(root, 'client-process.json'))).rejects.toMatchObject({ code: 'ENOENT' });
    service.plan.executable = process.execPath; await service.execute({ type: 'launch' }); expect(service.snapshot().status).toBe('running');
    await service.execute({ type: 'stop' }); expect(service.snapshot().runningBuild).toBeNull();
  }, 15000);
  test('a visible client launch preserves its window policy through the managed lifecycle', async () => {
    const { service } = await fixture(); service.plan.windowsHide = false;
    const actualStart = processes.startProcess;
    // Observe the real client policy, but keep this CLI-only helper hidden.
    const spawn = vi.spyOn(processes, 'startProcess').mockImplementation((executable, args, options) => actualStart(executable, args, { ...options, windowsHide: true }));
    await service.execute({ type: 'launch' });
    expect(spawn).toHaveBeenCalledWith(service.plan.executable, service.plan.args, expect.objectContaining({ windowsHide: false, cwd: root }));
    expect(service.snapshot().status).toBe('running');
    await service.execute({ type: 'stop' }); expect(service.snapshot().runningBuild).toBeNull();
  }, 15000);
  test('failed check then local helper start/end permits a successful manual check after connectivity returns', async () => {
    const { service, id, commit } = await fixture();
    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('offline fixture'); }));
    await service.execute({ type: 'check' }); await service.idle(); expect(service.snapshot().online).toBe(false); expect(service.snapshot().error).toBeTruthy();
    await service.execute({ type: 'launch' }); expect(service.snapshot().error).toBeNull(); await service.execute({ type: 'stop' });
    expect(service.snapshot()).toMatchObject({ online: false, status: 'offline', runningBuild: null, error: null });
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ sha: commit }), { status: 200 })));
    await service.execute({ type: 'check' }); const checked = await service.idle();
    expect(checked).toMatchObject({ online: true, status: 'ready', selected: id, latestCommit: commit, error: null });
  }, 15000);
  test('failed termination does not settle exited, and the same live handle can be stopped successfully on retry', async () => {
    let denied = true; const group = new ProcessGroup({ terminate: async child => { if (denied) throw new Error('termination denied fixture'); await processes.killTree(child); } });
    const handle = processes.startProcess(process.execPath, ['-e', 'setInterval(()=>{},1000)'], { cwd: root, env: childEnvironment(root, root), group, line: () => {} });
    const child = await handle.started; let ended = false; void handle.exited.then(() => { ended = true; }, () => { ended = true; });
    try {
      await expect(handle.stop()).rejects.toThrow('termination denied');
      expect(child.exitCode).toBeNull(); expect(() => process.kill(child.pid!, 0)).not.toThrow(); expect(ended).toBe(false); expect(group.activeCount).toBe(1);
      denied = false; await handle.stop(); expect(ended).toBe(true); expect(group.activeCount).toBe(0);
    } finally { denied = false; await handle.stop(); }
  }, 15000);
  test('failed client stop and failed shutdown preserve the live PID, lease and version guard until a successful retry', async () => {
    const { service, id } = await fixture(); await service.execute({ type: 'launch' }); const child = await service.claimed.promise; service.failTermination = true;
    await expect(service.execute({ type: 'stop' })).rejects.toThrow('termination denied');
    expect(service.terminationAttempts).toBe(1); expect(child.exitCode).toBeNull(); expect(() => process.kill(child.pid!, 0)).not.toThrow();
    expect(service.snapshot().runningBuild).toBe(id); expect(service.snapshot().error).toContain('still protected'); await fs.access(path.join(root, 'client-process.json'));
    await expect(service.execute({ type: 'launch' })).rejects.toThrow('already');
    await expect(service.shutdown()).rejects.toThrow('termination denied'); expect(service.terminationAttempts).toBe(2);
    await expect(service.execute({ type: 'launch' })).rejects.toThrow('already'); expect(service.snapshot().runningBuild).toBe(id); await fs.access(path.join(root, 'client-process.json'));
    service.failTermination = false; await service.execute({ type: 'stop' }); expect(service.terminationAttempts).toBe(3);
    expect(service.snapshot().runningBuild).toBeNull(); await expect(fs.access(path.join(root, 'client-process.json'))).rejects.toMatchObject({ code: 'ENOENT' });
  }, 15000);
  test('a hanging termination attempt rejects on its deadline without releasing the live process and still permits retry', async () => {
    let hanging = true;
    const group = new ProcessGroup({ stopTimeoutMs: 30, terminate: async child => { if (hanging) await new Promise<void>(() => {}); else await processes.killTree(child); } });
    const handle = processes.startProcess(process.execPath, ['-e', 'setInterval(()=>{},1000)'], { cwd: root, env: childEnvironment(root, root), group, line: () => {} }); const child = await handle.started;
    try {
      await expect(handle.stop()).rejects.toThrow('remains protected'); expect(child.exitCode).toBeNull(); expect(group.activeCount).toBe(1); expect(() => process.kill(child.pid!, 0)).not.toThrow();
      hanging = false; group.options.stopTimeoutMs = 10000; await handle.stop(); expect(group.activeCount).toBe(0);
    } finally { hanging = false; group.options.stopTimeoutMs = 10000; await handle.stop(); }
  }, 15000);
  test('failed background cancellation keeps the task protected and retryable instead of releasing a still-writing worker', async () => {
    const { service, id } = await fixture(); service.beginBackgroundHelper(); const child = await service.taskStarted.promise; service.failTermination = true;
    await expect(service.execute({ type: 'cancel' })).rejects.toThrow('termination denied'); expect(service.terminationAttempts).toBe(1);
    expect(child.exitCode).toBeNull(); expect(() => process.kill(child.pid!, 0)).not.toThrow(); expect(service.snapshot().selected).toBe(id); expect(service.snapshot().status).toBe('prepare');
    await expect(service.execute({ type: 'launch' })).rejects.toThrow('active'); await expect(service.execute({ type: 'check' })).rejects.toThrow('Another task');
    await expect(service.shutdown()).rejects.toThrow('termination denied'); expect(service.terminationAttempts).toBe(2);
    await expect(service.execute({ type: 'launch' })).rejects.toThrow('active');
    service.failTermination = false; await service.execute({ type: 'cancel' }); const stopped = await service.idle();
    expect(service.terminationAttempts).toBe(3); expect(stopped).toMatchObject({ status: 'cancelled', selected: id });
  }, 15000);
});
