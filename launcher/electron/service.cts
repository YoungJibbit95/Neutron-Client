import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { EventEmitter } from 'node:events';
import type { ChildProcess } from 'node:child_process';
import { atomicJson, owned, redact } from './files.cjs';
import { Logs } from './logs.cjs';
import { installTools, validateJava, verifyTools } from './toolchain.cjs';
import { installRuntime, verifyRuntime, type Runtime } from './minecraft.cjs';
import { installReleaseDistribution, launchArguments, verifyDistribution, reusableReleaseDistribution, clientClasspath } from './distribution.cjs';
import { listClientReleases } from './release-catalog.cjs';
import { childEnvironment, killTree, startProcess, ProcessCancelledError, ProcessGroup, type ManagedProcess } from './processes.cjs';
import { parseCommand, type BuildRecord, type ClientRelease, type Snapshot } from './contracts.cjs';
import type { LauncherRelease } from './alpha-release.cjs';
import { assertUserInstallation } from './installation-context.cjs';
import { validateClient } from './validate-client.cjs';
import { requireStorageCapacity } from './storage-location.cjs';
import { parseStoredState } from './stored-state.cjs';
import { commandHash, inspectProcess, processMatches, type ClientLease } from './lease.cjs';

interface Stored { schemaVersion: 1; options: Snapshot['options']; preferences: Snapshot['preferences']; builds: BuildRecord[]; selected: string | null; runtime: Runtime | null }
export interface PreparedClient { executable: string; args: string[]; cwd: string; env: NodeJS.ProcessEnv; temporary: string; windowsHide?: boolean }
export class LauncherService extends EventEmitter {
  private registryError: string | null = null; private installationCommitted = false;
  private installationResult: Awaited<ReturnType<LauncherService['verifyReleaseRecord']>> | null = null;
  readonly logs: Logs; private state: Snapshot; private runtime: Runtime | null = null;
  private task: AbortController | null = null; private taskPromise: Promise<void> | null = null; private child: ChildProcess | null = null;
  private clientPromise: Promise<void> | null = null; private timer: NodeJS.Timeout | null = null; private writes = Promise.resolve(); private commands = Promise.resolve(); private committing = false; private stopping = false;
  private leaseWrite = Promise.resolve(); private recovered: ClientLease | null = null; private recoveryTimer: NodeJS.Timeout | null = null; private recoveryChecking = false;
  private unverifiedLease = false;
  private starting: Promise<void> | null = null; private startController: AbortController | null = null; private clientHandle: ManagedProcess | null = null; private closing = false;
  private taskProcesses: ProcessGroup | null = null; private clientProcesses: ProcessGroup | null = null;
  private catalogController: AbortController | null = null; private catalogTimer: NodeJS.Timeout | null = null;
  constructor(readonly root: string, readonly installDirectory?: string, private readonly launcherRelease?: LauncherRelease) {
    super(); const physicalMiB = Math.floor(os.totalmem() / 1024 ** 2); const maxRamMiB = Math.max(1024, Math.min(8192, Math.floor((physicalMiB - 2048) / 256) * 256));
    this.state = { revision: 0, status: 'first-run', online: true, setupComplete: false, builds: [], selected: null, runningBuild: null, clientStarting: false, logs: [], releases: [], latestRelease: null, launcherVersion: launcherRelease?.launcherVersion ?? 'development', lastChecked: null, error: null, exitCode: null, physicalMiB, maxRamMiB, download: null,
      options: { ramMiB: Math.max(1024, Math.min(4096, Math.floor(physicalMiB / 4 / 256) * 256)), gameDir: path.join(root, 'game'), java: 'managed', javaPath: '', width: 1280, height: 720, fullscreen: false, jvmArgs: '' }, preferences: { theme: 'sunset', locale: 'de', scale: 100, effects: 70, reducedMotion: false } };
    this.logs = new Logs(root, () => this.changed());
  }
  snapshot(): Snapshot { return structuredClone({ ...this.state, logs: this.logs.lines }); }
  private changed() {
    this.state.revision++;
    if (!this.timer) this.timer = setTimeout(() => { this.timer = null; this.emit('snapshot', this.snapshot()); }, 80);
  }
  private async persist() {
    if (this.registryError) throw new Error(this.registryError);
    const data: Stored = { schemaVersion: 1, options: structuredClone(this.state.options), preferences: structuredClone(this.state.preferences), builds: structuredClone(this.state.builds), selected: this.state.selected, runtime: this.runtime };
    const operation = this.writes.then(() => atomicJson(path.join(this.root, 'state.json'), data)); this.writes = operation.catch(() => {}); await operation;
  }
  async initialize() {
    if (!path.isAbsolute(this.root)) throw new Error('Launcher data path must be absolute.');
    await owned(this.root); await fs.mkdir(this.root, { recursive: true });
    for (const folder of ['temp', 'logs', 'cache', 'work', 'versions', 'candidates', 'tools', 'runtimes']) await fs.mkdir(await owned(this.root, folder), { recursive: true });
    await this.logs.restore();
    const file = await owned(this.root, 'state.json'); let loaded = false; let registryAccepted = false;
    try {
      const data = JSON.parse(await fs.readFile(file, 'utf8')) as Stored; loaded = true;
      const { options, preferences } = parseStoredState(data);
      registryAccepted = true; this.registryError = null;
      this.state.options = { ...this.state.options, ...options }; this.state.preferences = { ...this.state.preferences, ...preferences };
      this.state.builds = data.builds; this.state.selected = data.builds.some(build => build.id === data.selected) ? data.selected : data.builds[0]?.id ?? null;
      this.runtime = data.runtime;
      if (this.runtime && (!/^runtimes\/[a-f0-9-]{36}$/.test(this.runtime.directory) || !/^[a-f0-9]{64}$/.test(this.runtime.manifestSha256))) throw new Error('Invalid runtime registry.');
      if (this.runtime) { await Promise.all([verifyTools(this.root), verifyRuntime(this.root, this.runtime)]); this.state.setupComplete = true; }
      this.state.status = this.state.setupComplete ? this.state.selected ? 'ready' : 'empty' : 'first-run';
    } catch (error) {
      if (loaded || (error as NodeJS.ErrnoException).code !== 'ENOENT') {
        if (!registryAccepted) this.registryError = redact('Saved state is unreadable or invalid. Repair state.json before setup; it has not been overwritten. ' + String(error));
        this.state.error = this.registryError ?? redact('Saved state/toolchain needs attention: ' + String(error)); this.logs.add('build', this.state.error, 'error');
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
    } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') { this.unverifiedLease = true; this.state.runningBuild = 'unverified-client'; this.state.status = 'error'; this.state.error = redact('Previous client process could not be verified. New launches/installations are blocked until the process record is checked: ' + String(error)); this.logs.add('build', this.state.error, 'error'); } }
    this.logs.add('build', this.state.runningBuild ? 'Neutron Launcher ready. Previous client is protected.' : 'Neutron Launcher ready.'); this.changed(); return this.snapshot();
  }
  protected createProcessGroup() { return new ProcessGroup({ onStopError: error => this.setError('Failed to stop an owned process; it remains protected until it actually exits. Retry Stop/Cancel: ' + String(error)) }); }
  protected startTask(work: (signal: AbortSignal, group: ProcessGroup) => Promise<void>) {
    if (this.registryError) throw new Error(this.registryError);
    if (this.closing || this.task || this.state.runningBuild) throw new Error('Another task or client is running, or the launcher is closing.');
    this.catalogController?.abort();
    this.installationCommitted = false; this.installationResult = null;
    const controller = new AbortController(); const group = this.createProcessGroup(); this.taskProcesses = group; this.task = controller; this.state.status = 'prepare'; this.state.error = null; this.changed();
    this.taskPromise = (async () => {
      let failure: unknown = null; let cancelled = false;
      try { await work(controller.signal, group); try { await this.logs.flush(); } catch (error) { if (!this.installationCommitted) throw error; console.error('Neutron is installed; diagnostic flush failed:', error); } }
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
    try {
      const releases = await listClientReleases(signal); signal.throwIfAborted();
      this.state.releases = releases;
      this.state.latestRelease = (releases.find(release => !release.prerelease) ?? releases[0])?.tag ?? null;
      this.state.lastChecked = new Date().toISOString(); this.state.online = true;
    }
    catch (error) { if (signal.aborted) throw error; this.state.online = false; throw error; }
  }
  private availableStatus(): Snapshot['status'] {
    if (!this.state.setupComplete) return 'first-run';
    if (!this.state.selected) return 'empty';
    if (!this.state.online) return 'offline';
    const selected = this.state.builds.find(build => build.id === this.state.selected);
    return this.state.latestRelease && selected?.releaseTag !== this.state.latestRelease ? 'update' : 'ready';
  }
  // Desktop-only metadata refresh. It never installs a client or interrupts play.
  // A failed refresh keeps the last catalog and all installed versions available.
  async refreshReleases() {
    if (this.closing || this.task || this.state.runningBuild || this.catalogController) return;
    const controller = new AbortController(); this.catalogController = controller;
    try {
      await this.check(controller.signal);
      if (!controller.signal.aborted && !this.task && !this.state.runningBuild && !['error', 'cancelled', 'crash'].includes(this.state.status)) this.state.status = this.availableStatus();
    } catch (error) {
      if (!controller.signal.aborted) {
        if (this.state.selected && !this.task && !this.state.runningBuild && !['error', 'cancelled', 'crash'].includes(this.state.status)) this.state.status = 'offline';
        this.logs.add('build', 'Could not refresh GitHub releases. Installed versions remain available. ' + redact(String(error)), 'warn');
      }
    } finally { if (this.catalogController === controller) this.catalogController = null; this.changed(); }
  }
  startReleaseChecks() {
    if (this.catalogTimer) return;
    void this.refreshReleases();
    this.catalogTimer = setInterval(() => { void this.refreshReleases(); }, 5 * 60 * 1000);
    this.catalogTimer.unref();
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
        if (this.committing) throw new Error('Wait for version activation to finish before saving settings.');
        if (this.state.runningBuild) throw new Error('Stop the client before editing launch options.');
        this.state.options = { ...this.state.options, ...command.patch }; await this.persist(); break;
      case 'save-preferences':
        if (this.committing) throw new Error('Wait for version activation to finish before saving settings.');
        this.state.preferences = { ...this.state.preferences, ...command.patch }; await this.persist(); break;
      case 'setup': this.startTask((signal, group) => this.provisionRelease(signal, group, undefined, false, true)); break;
      case 'check': this.startTask(async signal => { await this.check(signal); this.state.status = this.availableStatus(); }); break;
      case 'install': this.startTask((signal, group) => this.provisionRelease(signal, group, command.tag, command.play)); break;
      case 'launch': if (this.task) throw new Error('Wait for the active operation.'); await this.launch(); break;
      case 'stop': await this.stop(); break;
      case 'cancel': if (this.task) { if (!this.committing) { this.task.abort(new Error('Cancelled by user.')); await this.taskProcesses?.stop(); } await this.taskPromise; } break;
      case 'select': case 'remove': {
        if (this.task || this.state.runningBuild) throw new Error('A running build is protected.');
        const record = this.state.builds.find(build => build.id === command.id); if (!record) throw new Error('Installed version not found.');
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
  // The installer succeeds only after the downloaded pair and full runtime
  // pass local verification. Publishing a new GitHub release needs no new launcher.
  async prepareRelease() {
    if (!this.launcherRelease) throw new Error('This package has no Neutron launcher release metadata.');
    if (this.registryError) throw new Error(this.registryError);
    await this.execute({ type: 'setup' }); await this.taskPromise;
    if (!this.installationCommitted || !this.installationResult) throw new Error(this.state.error ?? 'Neutron preparation did not finish.');
    return this.installationResult;
  }
  async verifyRelease() {
    if (!this.launcherRelease) throw new Error('This package has no Neutron launcher release metadata.');
    if (this.registryError) throw new Error(this.registryError);
    const record = this.state.builds.find(build => build.id === this.state.selected);
    if (!this.state.setupComplete || !record?.releaseTag) throw new Error('No downloaded Neutron release is installed. Run setup again.');
    return this.verifyReleaseRecord(record);
  }
  private async verifyReleaseRecord(record: BuildRecord) {
    const began = performance.now();
    const checked = await verifyDistribution(this.root, record);
    if (checked.manifest.schemaVersion !== 2) throw new Error('This client was built from source. Install a GitHub release first.');
    const manifest = checked.manifest;
    const clientJar = manifest.classpath.find(item => item.path === 'neutron-client.jar');
    if (!clientJar || clientJar.sha256 !== manifest.release.jar.sha256 || manifest.versionJson.sha256 !== manifest.release.json.sha256) throw new Error('Installed Neutron release integrity failed.');
    const [tools] = await Promise.all([verifyTools(this.root), verifyRuntime(this.root, manifest.runtime)]);
    return { schemaVersion: 2, launcherVersion: this.launcherRelease?.launcherVersion ?? 'development', clientCommit: record.commit, selectedBuildId: record.id,
      releaseId: manifest.release.id, releaseTag: manifest.release.tag, clientVersion: manifest.release.version,
      clientManifestSha256: record.manifestSha256, clientJarSha256: clientJar.sha256, versionJsonSha256: manifest.versionJson.sha256,
      toolchainId: tools.toolchainId, verificationMs: Math.round(performance.now() - began) };
  }
  private async provisionRelease(signal: AbortSignal, group: ProcessGroup, tag?: string, play = false, installer = false) {
    this.requireWindows(); const began = performance.now(); const launchOptions = structuredClone(this.state.options);
    if (installer) await assertUserInstallation(path.join(this.root, 'temp'), signal, group);
    let release: ClientRelease | undefined; let installedRuntime: Runtime | null = null; let offlineInstall = process.env.NEUTRON_OFFLINE === '1';
    const installedRelease = async () => {
      const installed = this.state.builds.find(build => build.id === this.state.selected);
      if (!installed || tag && installed.releaseTag !== tag) throw new Error('Offline setup needs an intact installed Neutron release. Connect to download or repair it.');
      const checked = await verifyDistribution(this.root, installed);
      if (checked.manifest.schemaVersion !== 2) throw new Error('Offline setup needs a previously downloaded Neutron release. Connect to install it.');
      this.state.online = false; installedRuntime = checked.manifest.runtime; return checked.manifest.release;
    };
    if (offlineInstall) {
      release = await installedRelease();
    } else {
      // Refresh before installation so the renderer cannot inject an asset URL
      // or silently install a withdrawn/replaced catalog entry.
      try { await this.check(signal); }
      catch (error) {
        signal.throwIfAborted();
        if (!installer || tag) throw error;
        release = await installedRelease(); offlineInstall = true;
        this.logs.add('build', 'GitHub is unavailable. Setup will verify the already installed release offline; the latest version could not be checked.', 'warn');
      }
      if (!offlineInstall) {
        const chosen = tag ?? this.state.latestRelease;
        const found = this.state.releases.find(item => item.tag === chosen);
        if (!found) throw new Error(tag ? 'This Neutron release is no longer available. Refresh the version list.' : 'No compatible Neutron JAR and JSON release is published yet.');
        release = found;
      }
    }
    if (!release) throw new Error('No Neutron release was selected.');
    const requireCapacity = async () => { const capacity = await fs.statfs(this.root); requireStorageCapacity(this.root, capacity.bavail * capacity.bsize); };
    this.state.status = 'download'; this.changed();
    let tools: Awaited<ReturnType<typeof verifyTools>>;
    try { tools = await verifyTools(this.root); }
    catch { signal.throwIfAborted(); if (offlineInstall) throw new Error('Offline setup needs an intact managed Java runtime. Connect to repair it.'); await requireCapacity(); tools = await installTools(this.root, signal, this.progress, group); }
    let runtime = offlineInstall ? installedRuntime : this.runtime;
    if (runtime) { try { await verifyRuntime(this.root, runtime); } catch { signal.throwIfAborted(); runtime = null; } }
    if (!runtime) { if (offlineInstall) throw new Error('Offline setup needs an intact Minecraft runtime. Connect to repair it.'); await requireCapacity(); runtime = await installRuntime(this.root, signal, this.progress); }
    await verifyRuntime(this.root, runtime); signal.throwIfAborted();
    const reusable = await reusableReleaseDistribution(this.root, this.state.builds, release, runtime, signal);
    if (!reusable && offlineInstall) throw new Error('Offline setup needs an intact verified Neutron release. Connect to repair it. Previous version retained.');
    if (!reusable) await requireCapacity();
    const record = reusable ?? await installReleaseDistribution(this.root, release, runtime, signal, this.progress,
      phase => { this.state.status = phase; this.changed(); });
    signal.throwIfAborted(); this.state.status = 'verify'; this.changed();
    await validateClient(this.root, record, tools, signal, group);
    const result = await this.verifyReleaseRecord(record);
    await this.logs.flush();
    // The receipt and every required check precede the single registry commit.
    await atomicJson(await owned(this.root, 'logs/installer-result.json'), { ...result, phase: 'verified-before-activation',
      completedAt: new Date().toISOString(), setupMs: Math.round(performance.now() - began) });
    signal.throwIfAborted();
    const previous = { builds: this.state.builds, selected: this.state.selected, runtime: this.runtime };
    this.state.builds = [record, ...this.state.builds.filter(build => build.id !== record.id)].slice(0, 100);
    this.state.selected = record.id; this.runtime = runtime; this.committing = true;
    try { await this.persist(); this.installationCommitted = true; this.installationResult = result; }
    catch (error) { this.state.builds = previous.builds; this.state.selected = previous.selected; this.runtime = previous.runtime; throw error; }
    finally { this.committing = false; }
    this.state.setupComplete = true; this.state.status = this.availableStatus();
    this.logs.add('build', 'Neutron ' + release.version + ' downloaded and verified. Previous versions and personal data retained.');
    if (play) {
      if (JSON.stringify(launchOptions) !== JSON.stringify(this.state.options)) throw new Error('Launch options changed during installation. Start the installed version manually.');
      await this.launch();
    }
  }
  protected requireWindows() { if (process.platform !== 'win32' || process.arch !== 'x64') throw new Error('This launcher supports Windows x64 only.'); }
  protected async prepareClient(record: BuildRecord, signal: AbortSignal, group: ProcessGroup): Promise<PreparedClient> {
    signal.throwIfAborted();
    const distribution = await verifyDistribution(this.root, record); signal.throwIfAborted();
    const [runtime, tools] = await Promise.all([verifyRuntime(this.root, distribution.manifest.runtime), verifyTools(this.root)]); signal.throwIfAborted();
    const options = structuredClone(this.state.options); if (!path.isAbsolute(options.gameDir)) throw new Error('Choose an absolute game directory.');
    const protectedFolders = [...['versions', 'candidates', 'tools', 'runtimes', 'work', 'cache'].map(name => path.join(this.root, name)), ...(this.installDirectory ? [this.installDirectory] : [])];
    const checkGame = (game: string) => { for (const folder of protectedFolders) { const relative = path.relative(folder, game); if (!relative.startsWith('..') && !path.isAbsolute(relative)) throw new Error('Choose a game directory outside the application installation and managed launcher files.'); } };
    checkGame(path.resolve(options.gameDir)); await fs.mkdir(options.gameDir, { recursive: true }); const game = await fs.realpath(options.gameDir); checkGame(game); options.gameDir = game;
    const temporary = await owned(this.root, 'temp/client'); await fs.mkdir(temporary, { recursive: true });
    const java = await fs.realpath(options.java === 'custom' ? options.javaPath : path.join(tools.javaHome, 'bin/java.exe'));
    if (!path.isAbsolute(java)) throw new Error('Select an absolute Java 8 executable.');
    const javaHome = await validateJava(java, temporary, signal, group); signal.throwIfAborted();
    const classpath = clientClasspath(distribution, runtime);
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
    const record = this.state.builds.find(build => build.id === this.state.selected); if (!record) throw new Error('Install a Neutron release first.');
    this.catalogController?.abort();
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
  async shutdown(preserveRecoveredClient = false) {
    this.closing = true; this.stopping = true; this.catalogController?.abort(); if (this.catalogTimer) { clearInterval(this.catalogTimer); this.catalogTimer = null; } this.startController?.abort(new Error('Launcher closing.')); this.task?.abort(new Error('Launcher closing.'));
    try {
      await Promise.all([this.taskProcesses?.stop(), this.clientProcesses?.stop()]);
      await this.taskPromise; if (!preserveRecoveredClient || !this.recovered) await this.stop(); await this.leaseWrite.catch(() => {}); try { await this.logs.flush(); } catch (error) { if (!this.installationCommitted) throw error; console.error('Neutron is installed; diagnostic flush failed:', error); } await this.writes;
      if (this.timer) clearTimeout(this.timer); if (this.recoveryTimer) clearInterval(this.recoveryTimer);
    } catch (error) { this.closing = false; throw error; }
  }
  report() { return redact(JSON.stringify({ product: 'Neutron Launcher ' + (this.launcherRelease?.launcherVersion ?? 'development'), platform: 'windows-x64', selected: this.state.builds.find(build => build.id === this.state.selected)?.commit ?? null, exitCode: this.state.exitCode, error: this.state.error, logs: this.logs.lines.slice(-80) }, null, 2)); }
  setError(error: unknown) { this.state.error = redact(String(error)); this.logs.add('build', this.state.error, 'error'); this.changed(); }
}
