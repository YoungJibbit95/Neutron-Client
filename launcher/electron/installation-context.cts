import path from 'node:path';
import { childEnvironment, runProcess, type ProcessGroup } from './processes.cjs';

// Also guard the headless entry when invoked outside NSIS. The fixed probe
// checks the current token, not mere membership in the Administrators group.
export async function assertUserInstallation(temporary: string, signal: AbortSignal, group?: ProcessGroup) {
  const powershell = path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32/WindowsPowerShell/v1.0/powershell.exe');
  let result = '';
  const code = await runProcess(powershell, ['-NoProfile', '-NonInteractive', '-Command',
    '$p=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent());if($p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){"ELEVATED"}else{"USER"}'],
  { cwd: temporary, env: childEnvironment('', temporary), signal, group, line: line => { result += line.trim(); } });
  if (code !== 0 || result !== 'USER') throw new Error('Run Alpha setup normally in your own Windows account, without Run as administrator. Elevated client preparation is refused.');
}
