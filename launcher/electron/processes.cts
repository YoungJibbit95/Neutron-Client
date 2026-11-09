import { spawn, type ChildProcess } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';

export function childEnvironment(javaHome: string, temporary: string) {
  const env = { ...process.env };
  for (const key of Object.keys(env)) if (['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'MAVEN_ARGS', 'CLASSPATH'].includes(key.toUpperCase())) delete env[key];
  return { ...env, JAVA_HOME: javaHome, TEMP: temporary, TMP: temporary };
}
export async function killTree(child: Pick<ChildProcess, 'pid' | 'exitCode'>) {
  if (!child.pid || child.exitCode !== null) return;
  if (process.platform === 'win32') {
    const systemRoot = process.env.SystemRoot ?? 'C:\\Windows';
    await new Promise<void>((resolve, reject) => {
      const killer = spawn(path.join(systemRoot, 'System32/taskkill.exe'), ['/PID', String(child.pid), '/T', '/F'], { shell: false, windowsHide: true, stdio: 'ignore' });
      killer.once('error', reject); killer.once('close', code => code === 0 || child.exitCode !== null ? resolve() : reject(new Error('Could not stop the process tree.')));
    });
  } else { process.kill(child.pid, 'SIGTERM'); }
}
export type Terminator = (child: Pick<ChildProcess, 'pid' | 'exitCode'>) => Promise<void>;
export class ProcessGroup {
  private handles = new Set<ManagedProcess>();
  constructor(readonly options: { terminate?: Terminator; onStopError?: (error: unknown) => void; stopTimeoutMs?: number } = {}) {}
  get activeCount() { return this.handles.size; }
  add(handle: ManagedProcess) { this.handles.add(handle); const remove = () => this.handles.delete(handle); void handle.exited.then(remove, remove); }
  report(error: unknown) { this.options.onStopError?.(error); }
  async stop() { try { await Promise.all([...this.handles].map(handle => handle.stop())); } catch (error) { this.report(error); throw error; } }
  async idle() { while (this.handles.size) await Promise.all([...this.handles].map(handle => handle.exited.catch(() => {}))); }
}
export interface ProcessOptions { cwd: string; env: NodeJS.ProcessEnv; windowsHide?: boolean; signal?: AbortSignal; group?: ProcessGroup; line: (line: string, error: boolean) => void }
export interface ManagedProcess { started: Promise<ChildProcess>; exited: Promise<number>; stop: () => Promise<void> }
export class ProcessCancelledError extends Error {
  constructor(reason: unknown, readonly exitCode: number) { super(reason instanceof Error ? reason.message : String(reason)); this.name = 'ProcessCancelledError'; }
}
function stopDeadline(operation: Promise<void>, milliseconds: number) {
  return new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Process termination did not complete; the process remains protected. Retry Stop/Cancel.')), milliseconds); timer.unref();
    void operation.then(() => { clearTimeout(timer); resolve(); }, error => { clearTimeout(timer); reject(error); });
  });
}
// The handle exists before directory preparation. Callers can reserve, stop or
// await this same start even while no OS process has been spawned yet.
export function startProcess(executable: string, args: string[], options: ProcessOptions): ManagedProcess {
  const signal = options.signal; const terminate = options.group?.options.terminate ?? killTree;
  let child: ChildProcess | null = null; let spawned = false; let ended = false; let stopRequested = false; let stopAttempt: Promise<void> | null = null;
  let resolveStarted!: (child: ChildProcess) => void; let rejectStarted!: (error: unknown) => void;
  const started = new Promise<ChildProcess>((resolve, reject) => { resolveStarted = resolve; rejectStarted = reject; });
  const checkStart = () => { signal?.throwIfAborted(); if (stopRequested) throw new ProcessCancelledError(new Error('Process start cancelled.'), -1); };
  const exited = (async () => {
    try {
      checkStart(); await fs.mkdir(options.cwd, { recursive: true }); checkStart();
      return await new Promise<number>((resolve, reject) => {
        const processChild = spawn(executable, args, { cwd: options.cwd, env: options.env, shell: false, windowsHide: options.windowsHide ?? true, stdio: ['ignore', 'pipe', 'pipe'] }); child = processChild;
        processChild.once('spawn', () => { spawned = true; resolveStarted(processChild); });
        for (const [stream, error] of [[processChild.stdout, false], [processChild.stderr, true]] as const) {
          if (!stream) continue; let pending = ''; stream.setEncoding('utf8');
          stream.on('data', (chunk: string) => { pending += chunk; const lines = pending.split(/\r?\n|\r/); pending = lines.pop()!.slice(-65536); for (const line of lines) options.line(line.slice(0, 65536), error); });
          stream.on('end', () => { if (pending) options.line(pending, error); });
        }
        processChild.once('error', error => { if (!spawned) { ended = true; rejectStarted(error); reject(error); } else options.group?.report(error); });
        processChild.once('close', code => {
          ended = true;
          if (!spawned) rejectStarted(new Error('Process closed before the spawn handshake.'));
          if (signal?.aborted || stopRequested) reject(new ProcessCancelledError(signal?.reason ?? new Error('Process stopped by launcher.'), code ?? -1)); else resolve(code ?? -1);
        });
      });
    } catch (error) { if (!child) ended = true; rejectStarted(error); throw error; }
  })();
  const stop = () => {
    stopRequested = true;
    if (stopAttempt) return stopAttempt;
    const attempt = stopDeadline((async () => {
      const target = await started.catch(() => null);
      if (!target || ended) return;
      // A failed taskkill is a failed control attempt, never a process exit.
      // Preserve exited, ownership and the option to retry this exact handle.
      await terminate(target);
      try { await exited; } catch (error) { if (!ended) throw error; }
    })(), options.group?.options.stopTimeoutMs ?? 10000);
    stopAttempt = attempt;
    const clear = () => { if (stopAttempt === attempt) stopAttempt = null; };
    void attempt.then(clear, clear); return attempt;
  };
  const abort = () => { void stop().catch(error => options.group?.report(error)); };
  signal?.addEventListener('abort', abort, { once: true });
  const removeAbort = () => signal?.removeEventListener('abort', abort);
  void exited.then(removeAbort, removeAbort);
  void started.catch(() => {}); void exited.catch(() => {});
  const handle = { started, exited, stop }; options.group?.add(handle);
  if (signal?.aborted) abort();
  return handle;
}
export function runProcess(executable: string, args: string[], options: ProcessOptions): Promise<number> {
  return startProcess(executable, args, options).exited;
}
