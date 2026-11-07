import path from 'node:path';
import { createHash } from 'node:crypto';
import { runProcess, childEnvironment, type ProcessGroup } from './processes.cjs';
export interface Identity { pid: number; executable: string; commandLine: string; created: string }
export interface ClientLease { pid: number; executable: string; buildId: string; commandHash?: string; created?: string }
export async function inspectProcess(pid: number, temporary: string, group?: ProcessGroup, signal?: AbortSignal): Promise<Identity | null> {
  if (!Number.isSafeInteger(pid) || pid < 1) throw new Error('Invalid managed process ID.');
  const script = `$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $OutputEncoding=[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); $p = Get-CimInstance Win32_Process -Filter 'ProcessId = ${pid}'; if ($p) { @{pid=$p.ProcessId; executable=$p.ExecutablePath; commandLine=$p.CommandLine; created=$p.CreationDate.ToUniversalTime().ToString('O')} | ConvertTo-Json -Compress } else { 'null' }`;
  let output = ''; let errors = '';
  const executable = path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32/WindowsPowerShell/v1.0/powershell.exe');
  const timeout = AbortSignal.timeout(10000);
  const code = await runProcess(executable, ['-NoLogo', '-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-OutputFormat', 'Text', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')], { cwd: temporary, env: childEnvironment(path.dirname(process.execPath), temporary), signal: signal ? AbortSignal.any([signal, timeout]) : timeout, group, line: (line, error) => { if (error) errors += line; else output += line; } });
  if (code) throw new Error('Could not verify the previous client process: ' + errors);
  return JSON.parse(output) as Identity | null;
}
export function processMatches(root: string, lease: ClientLease, identity: Identity | null) {
  if (!identity || identity.pid !== lease.pid || typeof identity.commandLine !== 'string' || typeof identity.executable !== 'string') return false;
  if (path.resolve(identity.executable).toLowerCase() !== path.resolve(lease.executable).toLowerCase()) return false;
  if (!identity.commandLine.includes('me.neutron.launcher.ClientBootstrap') || !identity.commandLine.toLowerCase().includes(path.join(root, 'versions', lease.buildId).toLowerCase())) return false;
  return (!lease.created || lease.created === identity.created) && (!lease.commandHash || lease.commandHash === commandHash(identity));
}
export function commandHash(identity: Identity) { return createHash('sha256').update(identity.commandLine).digest('hex'); }
