import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { EventEmitter } from 'node:events';
import type { ChildProcess } from 'node:child_process';
import { atomicJson, owned, redact } from './files.cjs';
import { Logs } from './logs.cjs';
import { installTools, validateJava, verifyTools } from './toolchain.cjs';
import { installRuntime, verifyRuntime, type Runtime } from './minecraft.cjs';
import { buildDistribution, launchArguments, resolveMain, verifyDistribution, reusableDistribution } from './distribution.cjs';
import { childEnvironment, killTree, startProcess, ProcessCancelledError, ProcessGroup, type ManagedProcess } from './processes.cjs';
import { optionPatch, parseCommand, preferencePatch, type BuildRecord, type Command, type Snapshot } from './contracts.cjs';
import { commandHash, inspectProcess, processMatches, type ClientLease } from './lease.cjs';

interface Stored { schemaVersion: 1; options: Snapshot['options']; preferences: Snapshot['preferences']; builds: BuildRecord[]; selected: string | null; runtime: Runtime | null }
export interface PreparedClient { executable: string; args: string[]; cwd: string; env: NodeJS.ProcessEnv; temporary: string; windowsHide?: boolean }
const busy = (status: Snapshot['status']) => ['prepare', 'download', 'build', 'verify'].includes(status);
export class LauncherService extends EventEmitter {
  readonly logs: Logs; private state: Snapshot; private runtime: Runtime | null = null;
  private task: AbortController | null = null; private taskPromise: Promise<void> | null = null; private child: ChildProcess | null = null;
  private clientPromise: Promise<void> | null = null; private timer: NodeJS.Timeout | null = null; private writes = Promise.resolve(); private commands = Promise.resolve(); private committing = false; private stopping = false;
  private leaseWrite = Promise.resolve(); private recovered: ClientLease | null = null; private recoveryTimer: NodeJS.Timeout | null = null; private recoveryChecking = false;
  private unverifiedLease = false;
  private starting: Promise<void> | null = null; private startController: AbortController | null = null; private clientHandle: ManagedProcess | null = null; private closing = false;
  private taskProcesses: ProcessGroup | null = null; private clientProcesses: ProcessGroup | null = null;
  constructor(readonly root: string, readonly adapter: string, readonly installDirectory?: string) {
    super(); const physicalMiB = Math.floor(os.totalmem() / 1024 ** 2); const maxRamMiB = Math.max(1024, Math.min(8192, Math.floor((physicalMiB - 2048) / 256) * 256));
    this.state = { revision: 0, status: 'first-run', online: true, setupComplete: false, builds: [], selected: null, runningBuild: null, clientStarting: false, logs: [], latestCommit: null, lastChecked: null, error: null, exitCode: null, physicalMiB, maxRamMiB, download: null,
      options: { ramMiB: Math.max(1024, Math.min(4096, Math.floor(physicalMiB / 4 / 256) * 256)), gameDir: path.join(root, 'game'), java: 'managed', javaPath: '', width: 1280, height: 720, fullscreen: false, jvmArgs: '' }, preferences: { theme: 'sunset', locale: 'de', scale: 100, effects: 70, reducedMotion: false } };
    this.logs = new Logs(root, () => this.changed());
  }
  snapshot(): Snapshot { return structuredClone({ ...this.state, logs: this.logs.lines }); }
  private changed() {
    this.state.revision++;
    if (!this.timer) this.timer = setTimeout(() => { this.timer = null; this.emit('snapshot', this.snapshot()); }, 80);
  }
  private async persist() {
    const data: Stored = { schemaVersion: 1, options: structuredClone(this.state.options), preferences: structuredClone(this.state.preferences), builds: structuredClone(this.state.builds), selected: this.state.selected, runtime: this.runtime };
    const operation = this.writes.then(() => atomicJson(path.join(this.root, 'state.json'), data)); this.writes = operation.catch(() => {}); await operation;
  }
  async initialize() {
    if (!path.isAbsolute(this.root)) throw new Error('Launcher data path must be absolute.');
    await owned(this.root); await fs.mkdir(this.root, { recursive: true });
    for (const folder of ['temp', 'logs', 'cache', 'work', 'versions', 'candidates', 'tools', 'runtimes']) await fs.mkdir(await owned(this.root, folder), { recursive: true });
    await this.logs.restore();
    const file = await owned(this.root, 'state.json'); let loaded = false;
    try {
      const data = JSON.parse(await fs.readFile(file, 'utf8')) as Stored; loaded = true;
      if (data.schemaVersion !== 1 || !Array.isArray(data.builds) || data.builds.length > 100 || data.builds.some(build => !/^[a-f0-9]{40}-[a-f0-9-]{36}$/.test(build.id) || !/^[a-f0-9]{40}$/.test(build.commit) || !/^[a-f0-9]{64}$/.test(build.manifestSha256) || !Number.isFinite(build.sizeMiB) || build.sizeMiB < 0 || !Number.isFinite(Date.parse(build.date)))) throw new Error('Invalid persisted build registry.');
      const options = optionPatch(data.options); const preferences = preferencePatch(data.preferences);
      if (Object.keys(options).length !== 8 || Object.keys(preferences).length !== 5) throw new Error('Incomplete saved settings.');
      this.state.options = { ...this.state.options, ...options }; this.state.preferences = { ...this.state.preferences, ...preferences };
      this.state.builds = data.builds; this.state.selected = data.builds.some(build => build.id === data.selected) ? data.selected : data.builds[0]?.id ?? null;
      this.runtime = data.runtime;
      if (this.runtime && (!/^runtimes\/[a-f0-9-]{36}$/.test(this.runtime.directory) || !/^[a-f0-9]{64}$/.test(this.runtime.manifestSha256))) throw new Error('Invalid runtime registry.');
      if (this.runtime) { await verifyTools(this.root); await verifyRuntime(this.root, this.runtime); this.state.setupComplete = true; }
      this.state.status = this.state.setupComplete ? this.state.selected ? 'ready' : 'empty' : 'first-run';
    } catch (error) {
      if (loaded || (error as NodeJS.ErrnoException).code !== 'ENOENT') {
        this.state.error = redact('Saved state/toolchain needs attention: ' + String(error)); this.logs.add('build', this.state.error, 'error');
        // Preserve the original state for recovery; never replace it with defaults silently.
        await fs.copyFile(file, await owned(this.root, 'state-recovery-' + Date.now() + '.json')).catch(() => {});
      }
    }
    try {
      const lease = JSON.parse(await fs.readFile(await owned(this.root, 'client-process.json'), 'utf8')) as ClientLease;
      if (!this.state.builds.some(build => build.id === lease.buildId)) throw new Error('Previous process lease has an unknown build.');
      const identity = await inspectProcess(lease.pid, path.join(this.root, 'temp'));
      if (processMatches(this.root, lease, identity)) {
        this.recovered = { ...lease, created: identity!.created, commandHash: commandHash(identity!) }; this.state.runningBuild = lease.buildId; this.state.status = 'running';
        this.recoveryTimer = setInterval(() => { void this.refreshRecovered(); }, 3000);
        this.logs.add('client', 'Recovered the previous client process. Existing file output is retained; new stdout cannot be reattached after a launcher crash.', 'warn');
      } else await fs.unlink(await owned(this.root, 'client-process.json'));
    } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') { this.unverifiedLease = true; this.state.runningBuild = 'unverified-client'; this.state.status = 'error'; this.state.error = redact('Previous client process could not be verified. New launches/builds are blocked until the process record is checked: ' + String(error)); this.logs.add('build', this.state.error, 'error'); } }
    this.logs.add('build', this.state.runningBuild ? 'Neutron Launcher ready. Previous client is protected.' : 'Neutron Launcher ready.'); this.changed(); return this.snapshot();
  }
  protected createProcessGroup() { return new ProcessGroup({ onStopError: error => this.setError('Failed to stop an owned process; it remains protected until it actually exits. Retry Stop/Cancel: ' + String(error)) }); }
  protected startTask(work: (signal: AbortSignal, group: ProcessGroup) => Promise<void>) {
    if (this.closing || this.task || this.state.runningBuild) throw new Error('Another task or client is running, or the launcher is closing.');
    const controller = new AbortController(); const group = this.createProcessGroup(); this.taskProcesses = group; this.task = controller; this.state.status = 'prepare'; this.state.error = null; this.changed();
    this.taskPromise = (async () => {
      let failure: unknown = null; let cancelled = false;
      try { await work(controller.signal, group); await this.logs.flush(); }
      catch (error) { failure = error; cancelled = controller.signal.aborted; this.state.error = cancelled ? 'Operation cancelled. Installed versions are unchanged.' : redact(String(error)); this.logs.add('build', this.state.error, cancelled ? 'warn' : 'error'); controller.abort(error); }
      finally {
        // Failed termination must not release the task/versions while a child
        // continues to write into its workspace or dependency cache.
        await group.idle(); if (failure) this.state.status = cancelled ? 'cancelled' : 'error';
        this.task = null; this.taskProcesses = null; this.state.download = null; this.changed();
      }
    })();
  }
  private progress = (received: number, total: number | null) => { this.state.download = { received, total }; this.changed(); };
  private async check(signal: AbortSignal) {
    try { this.state.latestCommit = await resolveMain(signal); this.state.lastChecked = new Date().toISOString(); this.state.online = true; }
    catch (error) { if (signal.aborted) throw error; this.state.online = false; throw error; }
  }
  async execute(input: unknown): Promise<Snapshot> {
    const command = parseCommand(input);
    // Lifecycle controls must not queue behind a launch awaiting its handshake.
    if (command.type === 'stop') { await this.stop(); this.changed(); return this.snapshot(); }
    if (this.closing) throw new Error('The launcher is closing.');
    if (command.type === 'launch') { if (this.task) throw new Error('Wait for the active operation.'); await this.launch(); return this.snapshot(); }
    const operation = this.commands.then(() => this.executeCommand(input)); this.commands = operation.then(() => {}, () => {}); return operation;
  }
  private async executeCommand(input: unknown): Promise<Snapshot> {
    const command = parseCommand(input);
    switch (command.type) {
      case 'save-options':
        if (this.state.runningBuild) throw new Error('Stop the client before editing launch options.');
        this.state.options = { ...this.state.options, ...command.patch }; await this.persist(); break;
      case 'save-preferences': this.state.preferences = { ...this.state.preferences, ...command.patch }; await this.persist(); break;
      case 'setup': this.startTask(async (signal, group) => {
        this.requireWindows(); const capacity = await fs.statfs(this.root); if (capacity.bavail * capacity.bsize < 3 * 1024 ** 3) throw new Error('At least 3 GiB free space is required for setup.');
        this.state.status = 'download'; this.changed(); await installTools(this.root, signal, this.progress, group);
        const runtime = await installRuntime(this.root, signal, this.progress); await verifyRuntime(this.root, runtime); signal.throwIfAborted();
        const previous = this.runtime; this.runtime = runtime;
        this.committing = true;
        try { await this.persist(); } catch (error) { this.runtime = previous; throw error; } finally { this.committing = false; }
        this.state.setupComplete = true; this.state.status = this.state.selected ? 'ready' : 'empty'; this.logs.add('build', 'Java 8, Maven, assets and Windows x64 natives verified.');
      }); break;
      case 'check': this.startTask(async signal => { await this.check(signal); this.state.status = this.state.selected && this.state.builds.find(build => build.id === this.state.selected)?.commit === this.state.latestCommit ? 'ready' : 'update'; }); break;
      case 'build':
        if (!this.state.setupComplete || !this.runtime) throw new Error('Complete setup before building.');
        this.startTask(async (signal, group) => {
          this.requireWindows(); const capacity = await fs.statfs(this.root); if (capacity.bavail * capacity.bsize < 3 * 1024 ** 3) throw new Error('At least 3 GiB free space is required for a build.');
          const launchOptions = structuredClone(this.state.options); const tools = await verifyTools(this.root); await verifyRuntime(this.root, this.runtime!); await this.check(signal);
          const cached = await reusableDistribution(this.root, this.state.builds, this.state.latestCommit!, this.adapter, this.runtime!, signal);
          if (cached) this.logs.add('build', 'Verified cache hit: commit, adapter, runtime and all client files match.');
          const record = cached ?? await buildDistribution(this.root, this.state.latestCommit!, this.adapter, tools, this.runtime!, signal, phase => { this.state.status = phase; this.changed(); }, (line, error) => this.logs.add('build', line, /\[ERROR\]/.test(line) ? 'error' : error || /\[WARNING\]/.test(line) ? 'warn' : 'info'), group);
          signal.throwIfAborted(); const previous = { builds: this.state.builds, selected: this.state.selected };
          this.state.builds = [record, ...this.state.builds.filter(build => build.id !== record.id)].slice(0, 100); this.state.selected = record.id;
          this.committing = true;
          try { await this.persist(); } catch (error) { Object.assign(this.state, previous); throw error; } finally { this.committing = false; }
          this.state.status = 'ready'; this.logs.add('build', 'Verified build activated: ' + record.commit + '. Previous versions retained.');
          if (command.play) { if (JSON.stringify(launchOptions) !== JSON.stringify(this.state.options)) throw new Error('Launch options changed during build. Start the verified build manually.'); await this.launch(); }
        }); break;
      case 'launch': if (this.task) throw new Error('Wait for the active operation.'); await this.launch(); break;
      case 'stop': await this.stop(); break;
      case 'cancel': if (this.task) { if (!this.committing) { this.task.abort(new Error('Cancelled by user.')); await this.taskProcesses?.stop(); } await this.taskPromise; } break;
      case 'select': case 'remove': {
        if (this.task || this.state.runningBuild) throw new Error('A running build is protected.');
        const record = this.state.builds.find(build => build.id === command.id); if (!record) throw new Error('Build not found.');
        if (command.type === 'select') { await verifyDistribution(this.root, record); const previous = this.state.selected; this.state.selected = record.id; try { await this.persist(); } catch (error) { this.state.selected = previous; throw error; } this.state.status = this.state.online ? 'ready' : 'offline'; }
        else {
          if (record.id === this.state.selected) throw new Error('The selected version is protected.');
          // Registry removal only. Retain immutable bytes for recovery, never touch the profile.
          const previous = this.state.builds; this.state.builds = previous.filter(build => build.id !== record.id); try { await this.persist(); } catch (error) { this.state.builds = previous; throw error; }
        }
        break;
      }
      default: throw new Error('This command requires the desktop dialog host.');
    }
    this.changed(); return this.snapshot();
  }
  private requireWindows() { if (process.platform !== 'win32' || process.arch !== 'x64') throw new Error('This playtest supports Windows x64 only.'); }
  protected async prepareClient(record: BuildRecord, signal: AbortSignal, group: ProcessGroup): Promise<PreparedClient> {
    signal.throwIfAborted();
    const distribution = await verifyDistribution(this.root, record); signal.throwIfAborted();
    const runtime = await verifyRuntime(this.root, distribution.manifest.runtime); signal.throwIfAborted();
    const tools = await verifyTools(this.root); signal.throwIfAborted();
    const options = structuredClone(this.state.options); if (!path.isAbsolute(options.gameDir)) throw new Error('Choose an absolute game directory.');
    const protectedFolders = [...['versions', 'candidates', 'tools', 'runtimes', 'work', 'cache'].map(name => path.join(this.root, name)), ...(this.installDirectory ? [this.installDirectory] : [])];
    const checkGame = (game: string) => { for (const folder of protectedFolders) { const relative = path.relative(folder, game); if (!relative.startsWith('..') && !path.isAbsolute(relative)) throw new Error('Choose a game directory outside the application installation and managed launcher files.'); } };
    checkGame(path.resolve(options.gameDir)); await fs.mkdir(options.gameDir, { recursive: true }); const game = await fs.realpath(options.gameDir); checkGame(game); options.gameDir = game;
    const temporary = await owned(this.root, 'temp/client'); await fs.mkdir(temporary, { recursive: true });
    const java = await fs.realpath(options.java === 'custom' ? options.javaPath : path.join(tools.javaHome, 'bin/java.exe'));
    if (!path.isAbsolute(java)) throw new Error('Select an absolute Java 8 executable.');
    const javaHome = await validateJava(java, temporary, signal, group); signal.throwIfAborted();
    const classpath = [...runtime.manifest.javaLibraries.map(name => path.join(runtime.directory, name)), ...distribution.classpath];
    const args = launchArguments(options, this.state.maxRamMiB, classpath, runtime.directory, distribution.manifest.version, temporary);
    // javaw avoids a separate console, while allowing the game's first native
    // window to be shown. SW_HIDE on java.exe also hides LWJGL's game window.
    const executable = await fs.realpath(path.join(path.dirname(java), 'javaw.exe')).catch(() => { throw new Error('The selected Java 8 runtime is missing javaw.exe. Choose a complete Windows Java 8 runtime.'); });
    return { executable, args, cwd: game, env: childEnvironment(javaHome, temporary), temporary, windowsHide: false };
  }
  protected async claimClient(child: ChildProcess, record: BuildRecord, plan: PreparedClient, group: ProcessGroup, signal: AbortSignal) {
    signal.throwIfAborted();
    const lease: ClientLease = { pid: child.pid!, executable: plan.executable, buildId: record.id };
    const file = await owned(this.root, 'client-process.json'); await atomicJson(file, lease);
    signal.throwIfAborted(); const identity = await inspectProcess(lease.pid, plan.temporary, group, signal);
    if (processMatches(this.root, lease, identity)) await atomicJson(file, { ...lease, created: identity!.created, commandHash: commandHash(identity!) });
    else if (child.exitCode === null) throw new Error('Could not establish ownership of the client process.');
  }
  private launch(): Promise<void> {
    this.requireWindows();
    if (this.closing || this.unverifiedLease || this.starting || this.state.runningBuild) throw new Error('A client is already running/starting or its previous process record needs attention.');
    const record = this.state.builds.find(build => build.id === this.state.selected); if (!record) throw new Error('Build a version first.');
    // Reserve before preparation, mkdir or any other await. Stop/shutdown can
    // abort this reservation even when no child PID exists yet.
    const controller = new AbortController(); const group = this.createProcessGroup(); this.clientProcesses = group; this.startController = controller;
    this.state.runningBuild = record.id; this.state.clientStarting = true; this.state.status = 'prepare'; this.state.exitCode = null; this.state.error = null;
    this.stopping = false; this.clientPromise = null; this.leaseWrite = Promise.resolve(); this.changed();
    let handle: ManagedProcess | null = null; let operation!: Promise<void>; let startFailure: unknown = null;
    operation = (async () => {
      try {
        const plan = await this.prepareClient(record, controller.signal, group); controller.signal.throwIfAborted();
        handle = startProcess(plan.executable, plan.args, { cwd: plan.cwd, env: plan.env, windowsHide: plan.windowsHide, signal: controller.signal, group, line: (line, error) => this.logs.add('client', line, error ? 'warn' : 'info') });
        this.clientHandle = handle;
        const active = handle;
        this.clientPromise = (async () => {
          let code = -1; let failure: unknown = null;
          try { code = await active.exited; } catch (error) { failure = error; if (error instanceof ProcessCancelledError) code = error.exitCode; }
          // The lease can only be removed after this very start has completed
          // or failed; no delayed claim may recreate it after cleanup.
          await operation.catch(() => {}); await this.leaseWrite.catch(() => {});
          await fs.unlink(await owned(this.root, 'client-process.json')).catch(error => { if (error.code !== 'ENOENT') throw error; });
          this.child = null; this.clientHandle = null; this.state.runningBuild = null; this.state.clientStarting = false; this.state.exitCode = code;
          this.state.status = this.stopping || !failure && code === 0 ? this.state.online ? 'ready' : 'offline' : 'crash';
          if (this.stopping) this.state.error = null; else if (startFailure || failure) this.state.error = redact(String(startFailure ?? failure));
          this.logs.add('client', 'Client exited with code ' + code, this.state.status === 'crash' ? 'error' : 'info'); await this.logs.flush(); this.changed();
        })();
        void this.clientPromise.catch(error => this.setError(error));
        this.child = await active.started; controller.signal.throwIfAborted();
        this.leaseWrite = this.claimClient(this.child, record, plan, group, controller.signal); await this.leaseWrite; controller.signal.throwIfAborted();
        if (this.child.exitCode === null) this.state.status = 'running';
      } catch (error) {
        startFailure = error;
        if (handle && !controller.signal.aborted && !this.stopping) await group.stop();
        if (!handle) { this.state.runningBuild = null; this.state.status = this.stopping ? this.state.online ? 'ready' : 'offline' : 'error'; }
        else if (this.child?.exitCode === null) this.state.status = 'running';
        if (!this.stopping) { this.state.error = redact(String(error)); throw error; }
      } finally { this.state.clientStarting = false; this.changed(); }
    })();
    this.starting = operation;
    const clear = () => { if (this.starting === operation) { this.starting = null; this.startController = null; } };
    void operation.then(clear, clear);
    return operation.catch(async error => { if (!group.activeCount) await this.clientPromise; throw error; });
  }
  private async refreshRecovered() {
    if (!this.recovered || this.recoveryChecking) return; this.recoveryChecking = true;
    try {
      const lease = this.recovered; const identity = await inspectProcess(lease.pid, path.join(this.root, 'temp'));
      if (!processMatches(this.root, lease, identity)) { this.recovered = null; this.state.runningBuild = null; this.state.status = this.state.online ? 'ready' : 'offline'; if (this.recoveryTimer) clearInterval(this.recoveryTimer); await fs.unlink(await owned(this.root, 'client-process.json')).catch(error => { if (error.code !== 'ENOENT') throw error; }); this.changed(); }
    } catch (error) { this.setError(error); } finally { this.recoveryChecking = false; }
  }
  async stop() {
    this.stopping = true; this.startController?.abort(new Error('Client start cancelled by user.'));
    await this.clientProcesses?.stop();
    await this.starting?.catch(() => {});
    await this.clientPromise;
    if (this.recovered) { const identity = await inspectProcess(this.recovered.pid, path.join(this.root, 'temp')); if (processMatches(this.root, this.recovered, identity)) await killTree({ pid: this.recovered.pid, exitCode: null }); await this.refreshRecovered(); }
  }
  async idle() { await this.taskPromise; await this.logs.flush(); return this.snapshot(); }
  async shutdown() {
    this.closing = true; this.stopping = true; this.startController?.abort(new Error('Launcher closing.')); this.task?.abort(new Error('Launcher closing.'));
    try {
      await Promise.all([this.taskProcesses?.stop(), this.clientProcesses?.stop()]);
      await this.taskPromise; await this.stop(); await this.leaseWrite.catch(() => {}); await this.logs.flush(); await this.writes;
      if (this.timer) clearTimeout(this.timer); if (this.recoveryTimer) clearInterval(this.recoveryTimer);
    } catch (error) { this.closing = false; throw error; }
  }
  report() { return redact(JSON.stringify({ product: 'Neutron Launcher 0.1.0-playtest.5', platform: 'windows-x64', selected: this.state.builds.find(build => build.id === this.state.selected)?.commit ?? null, exitCode: this.state.exitCode, error: this.state.error, logs: this.logs.lines.slice(-80) }, null, 2)); }
  setError(error: unknown) { this.state.error = redact(String(error)); this.logs.add('build', this.state.error, 'error'); this.changed(); }
}
