export interface LaunchOptions {
  ramMiB: number; gameDir: string; java: 'managed' | 'custom'; javaPath: string;
  width: number; height: number; fullscreen: boolean; jvmArgs: string;
}
export interface Preferences { theme: 'sunset' | 'ocean' | 'violet' | 'graphite'; locale: 'de' | 'en'; scale: number; effects: number; reducedMotion: boolean }
export interface BuildRecord { id: string; commit: string; date: string; sizeMiB: number; manifestSha256: string }
export interface LogRecord { id: number; time: string; source: 'client' | 'build'; level: 'info' | 'warn' | 'error'; message: { de: string; en: string } }
export type Status = 'first-run' | 'empty' | 'ready' | 'offline' | 'update' | 'prepare' | 'download' | 'build' | 'verify' | 'cancelled' | 'error' | 'running' | 'crash';
export interface Snapshot {
  revision: number; status: Status; online: boolean; setupComplete: boolean; builds: BuildRecord[]; selected: string | null;
  runningBuild: string | null; clientStarting: boolean; options: LaunchOptions; preferences: Preferences; logs: LogRecord[];
  latestCommit: string | null; lastChecked: string | null; error: string | null; exitCode: number | null;
  physicalMiB: number; maxRamMiB: number; download: { received: number; total: number | null } | null;
}
export type Command =
  | { type: 'setup' | 'check' | 'launch' | 'stop' | 'cancel' | 'report' }
  | { type: 'build'; play: boolean }
  | { type: 'select' | 'remove'; id: string }
  | { type: 'save-options'; patch: Partial<LaunchOptions> }
  | { type: 'save-preferences'; patch: Partial<Preferences> }
  | { type: 'browse'; kind: 'game' | 'java' }
  | { type: 'export'; source: 'client' | 'build' }
  | { type: 'close'; choice: 'minimize' | 'quit' | 'stay' };
export interface Bridge {
  ready?: () => void;
  mode: 'live'; snapshot: () => Promise<Snapshot>; command: (command: Command) => Promise<Snapshot | string>;
  subscribe: (listener: (snapshot: Snapshot) => void) => () => void;
  onClose: (listener: () => void) => () => void;
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid command object.');
  return value as Record<string, unknown>;
}
function keys(value: Record<string, unknown>, allowed: readonly string[]) {
  if (Object.keys(value).some(key => !allowed.includes(key))) throw new Error('Unknown command field.');
}
function text(value: unknown, max: number) { return typeof value === 'string' && value.length <= max && !value.includes('\0'); }
export function optionPatch(value: unknown): Partial<LaunchOptions> {
  const patch = record(value); keys(patch, ['ramMiB', 'gameDir', 'java', 'javaPath', 'width', 'height', 'fullscreen', 'jvmArgs']);
  for (const [key, item] of Object.entries(patch)) {
    if (['ramMiB', 'width', 'height'].includes(key) ? !(Number.isInteger(item) && Number(item) >= 0 && Number(item) <= 32768)
      : key === 'fullscreen' ? typeof item !== 'boolean' : key === 'java' ? typeof item !== 'string' || !['managed', 'custom'].includes(item) : !text(item, key === 'jvmArgs' ? 2048 : 1024)) throw new Error('Invalid launch option: ' + key);
  }
  return patch;
}
export function preferencePatch(value: unknown): Partial<Preferences> {
  const patch = record(value); keys(patch, ['theme', 'locale', 'scale', 'effects', 'reducedMotion']);
  for (const [key, item] of Object.entries(patch)) {
    const valid = key === 'theme' ? typeof item === 'string' && ['sunset', 'ocean', 'violet', 'graphite'].includes(item) : key === 'locale' ? typeof item === 'string' && ['de', 'en'].includes(item) : key === 'scale' ? typeof item === 'number' && [90, 100, 110, 125].includes(item) : key === 'effects' ? Number.isInteger(item) && Number(item) >= 0 && Number(item) <= 100 : typeof item === 'boolean';
    if (!valid) throw new Error('Invalid preference: ' + key);
  }
  return patch;
}
export function parseCommand(value: unknown): Command {
  const command = record(value);
  switch (command.type) {
    case 'setup': case 'check': case 'launch': case 'stop': case 'cancel': case 'report': keys(command, ['type']); break;
    case 'build': keys(command, ['type', 'play']); if (typeof command.play !== 'boolean') throw new Error('Invalid play flag.'); break;
    case 'select': case 'remove': keys(command, ['type', 'id']); if (typeof command.id !== 'string' || !/^[a-f0-9]{40}-[a-f0-9-]{36}$/.test(command.id)) throw new Error('Invalid build ID.'); break;
    case 'save-options': keys(command, ['type', 'patch']); optionPatch(command.patch); break;
    case 'save-preferences': keys(command, ['type', 'patch']); preferencePatch(command.patch); break;
    case 'browse': keys(command, ['type', 'kind']); if (!['game', 'java'].includes(String(command.kind))) throw new Error('Invalid picker.'); break;
    case 'export': keys(command, ['type', 'source']); if (!['client', 'build'].includes(String(command.source))) throw new Error('Invalid log stream.'); break;
    case 'close': keys(command, ['type', 'choice']); if (!['minimize', 'quit', 'stay'].includes(String(command.choice))) throw new Error('Invalid close choice.'); break;
    default: throw new Error('Unknown launcher command.');
  }
  return command as unknown as Command;
}
