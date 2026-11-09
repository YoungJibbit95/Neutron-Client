import type { ClientRelease, Snapshot } from '../electron/contracts.cts';
export type Locale = 'de' | 'en';
export type Theme = 'sunset' | 'ocean' | 'violet' | 'graphite';
export type View = 'launch' | 'options' | 'settings' | 'update';
export const scenarios = ['ready', 'first-run', 'empty', 'offline', 'update', 'prepare', 'download', 'verify', 'cancelled', 'error', 'running', 'crash'] as const;
export type Scenario = typeof scenarios[number];
export type Phase = 'prepare' | 'download' | 'verify';
export const phases: Phase[] = ['prepare', 'download', 'verify'];
export type LogSource = 'client' | 'build';
export type Level = 'info' | 'warn' | 'error';
export interface LogLine { id: number; time: string; source: LogSource; level: Level; message: Record<Locale, string> }
export interface Build { id: string; commit: string; date: string; sizeMiB: number; releaseTag?: string; releaseId?: number; version?: string }
export interface Options {
  ramMiB: number; gameDir: string; java: 'managed' | 'custom'; javaPath: string;
  width: number; height: number; fullscreen: boolean; jvmArgs: string;
}
export interface Preferences { theme: Theme; locale: Locale; scale: number; effects: number; reducedMotion: boolean }
export interface State {
  view: View; status: Scenario; online: boolean; builds: Build[]; selected: string | null;
  runningBuild: string | null; autoLaunch: boolean; logs: LogLine[];
  setupComplete: boolean; releases: ClientRelease[]; latestRelease: string | null; launcherVersion: string; installingTag: string | null;
  options: Options; preferences: Preferences;
  runtime?: Pick<Snapshot, 'revision' | 'lastChecked' | 'error' | 'exitCode' | 'physicalMiB' | 'maxRamMiB' | 'download' | 'clientStarting'>;
}
export const MAX_LOG_LINES = 2000;
// All catalog data is illustrative and is available only in explicit UI previews.
function fixtureRelease(id: number, version: string, date: string, prerelease = false): ClientRelease {
  const tag = 'v' + version;
  return { id, tag, version, publishedAt: date, prerelease,
    jar: { id: id * 2, name: 'Neutron-' + version + '.jar', url: 'https://github.com/YoungJibbit95/Neutron-Client/releases/download/' + tag + '/Neutron-' + version + '.jar', sha256: 'a'.repeat(64), size: 199920502 },
    json: { id: id * 2 + 1, name: 'Neutron-' + version + '.json', url: 'https://github.com/YoungJibbit95/Neutron-Client/releases/download/' + tag + '/Neutron-' + version + '.json', sha256: 'b'.repeat(64), size: 9360 } };
}
export const fixtureReleases = [fixtureRelease(3, '0.3.0-beta.1', '2026-11-09T15:00:00Z', true), fixtureRelease(2, '0.2.2', '2026-11-09T13:00:00Z'), fixtureRelease(1, '0.2.1', '2026-11-08T15:00:00Z')];
export const fixtureBuilds: Build[] = [
  { id: 'local-eeddb7d', commit: 'eeddb7da58978f04fad95ad8042175b7ad848d6a', date: '2026-11-08T15:49:24Z', sizeMiB: 191, releaseTag: 'v0.2.1', releaseId: 1, version: '0.2.1' },
  { id: 'older-de1a515', commit: 'de1a515347013aec6072974dec29378fb75d32e8', date: '2026-10-27T18:20:00Z', sizeMiB: 128 },
];
export const nextFixture: Build = { id: 'preview-1bc6c36', commit: '1bc6c364b58a4eb86faf4cc9aab6e15153837cd9', date: '2026-11-09T16:00:00Z', sizeMiB: 191, releaseTag: 'v0.2.2', releaseId: 2, version: '0.2.2' };
export function initialState(scenario: Scenario = 'ready'): State {
  const empty = scenario === 'first-run' || scenario === 'empty';
  return {
    view: 'launch', status: scenario, online: scenario !== 'offline',
    builds: empty ? [] : fixtureBuilds.map(build => ({ ...build })), selected: empty ? null : fixtureBuilds[0].id,
    runningBuild: scenario === 'running' ? fixtureBuilds[0].id : null, autoLaunch: false, setupComplete: scenario !== 'first-run',
    releases: structuredClone(fixtureReleases), latestRelease: 'v0.2.2', launcherVersion: '0.3.0', installingTag: null,
    options: { ramMiB: 4096, gameDir: 'D:\\Games\\Neutron', java: 'managed', javaPath: '', width: 1280, height: 720, fullscreen: false, jvmArgs: '' },
    preferences: { theme: 'sunset', locale: 'de', scale: 100, effects: 70, reducedMotion: false },
    logs: [
      { id: 1, time: '2026-10-28T15:49:24Z', source: 'build', level: 'info', message: { de: 'Beispiel: Release-Dateien geprüft. Letzte spielbare Version bleibt verfügbar.', en: 'Example: release files verified. The last playable version stays available.' } },
      { id: 2, time: '2026-10-28T15:49:25Z', source: 'client', level: 'info', message: { de: 'Beispielkonsole bereit. Es läuft kein echter Client.', en: 'Example console ready. No real client is running.' } },
      { id: 3, time: '2026-10-28T15:49:26Z', source: 'client', level: 'info', message: { de: 'Java 8 x64 · Minecraft 1.8.9 · Profil LauncherSmoke (Beispiel)', en: 'Java 8 x64 · Minecraft 1.8.9 · LauncherSmoke profile (example)' } },
    ],
  };
}
export function isBusy(state: State) { return phases.includes(state.status as Phase); }
export function selectedBuild(state: State) { return state.builds.find(build => build.id === state.selected); }
export function canLaunch(state: State) { return !!selectedBuild(state) && !state.runningBuild && !isBusy(state) && !validateOptions(state.options).length && (!state.runtime || state.options.ramMiB <= state.runtime.maxRamMiB); }
export function selectedRelease(state: State, tag?: string) { return state.releases.find(release => release.tag === (tag || state.latestRelease)); }
export function canInstall(state: State, tag?: string) { return state.online && state.setupComplete && !!selectedRelease(state, tag) && !isBusy(state) && !state.runningBuild; }
export function validAbsolutePath(path: string) { return /^(?:[A-Za-z]:[\\/]|\/)/.test(path.trim()); }
export function validateOptions(options: Options): string[] {
  const errors: string[] = [];
  if (!Number.isInteger(options.ramMiB) || options.ramMiB < 1024 || options.ramMiB > 8192) errors.push('ram');
  if (!validAbsolutePath(options.gameDir)) errors.push('directory');
  if (options.java === 'custom' && !validAbsolutePath(options.javaPath)) errors.push('java');
  if (!Number.isInteger(options.width) || !Number.isInteger(options.height) || options.width < 640 || options.height < 480 || options.width > 7680 || options.height > 4320) errors.push('window');
  if (/(?:^|\s)["']?-Xm[xs]/i.test(options.jvmArgs) || /(?:^|\s)-XX:(?:MaxRAM|InitialRAM|MinRAM)(?:Percentage|Fraction|=)/i.test(options.jvmArgs)) errors.push('heap');
  return errors;
}
export function recommendedRam(physicalMiB: number) {
  return Math.max(1024, Math.min(4096, Math.floor(physicalMiB / 4 / 256) * 256));
}
export type Action =
  | { type: 'snapshot'; snapshot: Snapshot; preserveEdits?: boolean }
  | { type: 'browse'; kind: 'game' | 'java' }
  | { type: 'view'; view: View } | { type: 'scenario'; scenario: Scenario }
  | { type: 'preferences'; patch: Partial<Preferences> } | { type: 'options'; patch: Partial<Options> }
  | { type: 'launch' } | { type: 'stop' } | { type: 'check' } | { type: 'setup' }
  | { type: 'install'; tag?: string; play: boolean } | { type: 'next-phase' } | { type: 'cancel' } | { type: 'fail' }
  | { type: 'select'; id: string } | { type: 'remove'; id: string } | { type: 'append'; count: number };
function append(state: State, de: string, en: string, source: LogSource, level: Level = 'info'): State {
  const id = (state.logs.at(-1)?.id ?? 0) + 1;
  const line: LogLine = { id, time: new Date(Date.UTC(2026, 9, 28, 16, 0, id)).toISOString(), source, level, message: { de, en } };
  return { ...state, logs: [...state.logs, line].slice(-MAX_LOG_LINES) };
}
export function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'snapshot': {
      const snapshot = action.snapshot;
      if (state.runtime && snapshot.revision < state.runtime.revision) return state;
      return { ...state, ...snapshot, autoLaunch: false, options: action.preserveEdits ? state.options : snapshot.options, preferences: action.preserveEdits ? state.preferences : snapshot.preferences, runtime: snapshot };
    }
    case 'browse': return state;
    case 'view': return { ...state, view: action.view };
    case 'scenario': return { ...initialState(action.scenario), view: state.view, options: state.options, preferences: state.preferences };
    case 'preferences': return { ...state, preferences: { ...state.preferences, ...action.patch } };
    case 'options': return { ...state, options: { ...state.options, ...action.patch } };
    case 'launch': return canLaunch(state) ? append({ ...state, status: 'running', runningBuild: state.selected }, 'Simulation: Client gestartet.', 'Simulation: client started.', 'client') : state;
    case 'stop': return append({ ...state, status: state.online ? 'ready' : 'offline', runningBuild: null }, 'Simulation: Client beendet (Exitcode 0).', 'Simulation: client stopped (exit code 0).', 'client');
    case 'check': return state.online && !state.runningBuild && !isBusy(state) ? { ...state, status: state.setupComplete ? 'update' : 'first-run' } : state;
    case 'setup': return !state.setupComplete && state.online && !isBusy(state) ? append({ ...state, status: 'prepare', installingTag: state.latestRelease }, 'Simulation: Java, Minecraft-Ressourcen und neueste Release-Version vorbereiten.', 'Simulation: prepare Java, Minecraft resources and the latest release.', 'build') : state;
    case 'install': return canInstall(state, action.tag) && (!action.play || !validateOptions(state.options).length) ? append({ ...state, status: 'prepare', autoLaunch: action.play, installingTag: selectedRelease(state, action.tag)!.tag }, 'Simulation: veröffentlichte JAR und JSON herunterladen.', 'Simulation: download the published JAR and JSON.', 'build') : state;
    case 'next-phase': {
      const index = phases.indexOf(state.status as Phase);
      if (index < 0) return state;
      if (index < phases.length - 1) return append({ ...state, status: phases[index + 1] }, 'Simulation: nächste Installationsphase.', 'Simulation: next installation phase.', 'build');
      const start = state.autoLaunch && !validateOptions(state.options).length;
      const release = selectedRelease(state, state.installingTag ?? undefined);
      if (!release) return { ...state, status: 'error', autoLaunch: false, installingTag: null };
      const installed = state.builds.find(build => build.releaseId === release.id) ?? { ...nextFixture, id: release.tag === nextFixture.releaseTag ? nextFixture.id : 'preview-release-' + release.id, releaseId: release.id, releaseTag: release.tag, version: release.version };
      const updated = { ...state, setupComplete: true, builds: [installed, ...state.builds.filter(build => build.id !== installed.id)], selected: installed.id, status: (start ? 'running' : 'ready') as Scenario, runningBuild: start ? installed.id : null, autoLaunch: false, installingTag: null };
      return append(updated, 'Simulation: geprüft und aktiviert. Vorherige Version bleibt erhalten.', 'Simulation: verified and activated. Previous version retained.', 'build');
    }
    case 'cancel': return isBusy(state) ? append({ ...state, status: 'cancelled', autoLaunch: false, installingTag: null }, 'Simulation: abgebrochen. Installierte Versionen unverändert.', 'Simulation: cancelled. Installed versions unchanged.', 'build', 'warn') : state;
    case 'fail': return isBusy(state) ? append({ ...state, status: 'error', autoLaunch: false, installingTag: null }, 'Simulation: Installation fehlgeschlagen. Letzte Version bleibt spielbar.', 'Simulation: installation failed. Last version remains playable.', 'build', 'error') : state;
    case 'select': return !state.runningBuild && !isBusy(state) && state.builds.some(build => build.id === action.id) ? { ...state, selected: action.id } : state;
    case 'remove': return action.id === state.selected || action.id === state.runningBuild || isBusy(state) ? state : { ...state, builds: state.builds.filter(build => build.id !== action.id) };
    case 'append': {
      const first = (state.logs.at(-1)?.id ?? 0) + 1;
      const lines: LogLine[] = Array.from({ length: Math.max(0, Math.min(5000, Math.floor(action.count))) }, (_, index) => ({
        id: first + index, time: new Date(Date.UTC(2026, 9, 28, 16, 0, first + index)).toISOString(),
        source: index % 3 === 0 ? 'build' : 'client', level: index % 23 === 0 ? 'warn' : 'info',
        message: { de: 'Beispielausgabe: Ressourcen geladen und Frame vorbereitet.', en: 'Example output: resources loaded and frame prepared.' },
      }));
      return { ...state, logs: [...state.logs, ...lines].slice(-MAX_LOG_LINES) };
    }
  }
}
export function liveInitialState(): State {
  return { ...initialState('first-run'), online: false, releases: [], latestRelease: null, logs: [], options: { ramMiB: 4096, gameDir: '', java: 'managed', javaPath: '', width: 1280, height: 720, fullscreen: false, jvmArgs: '' }, runtime: { revision: -1, lastChecked: null, error: null, exitCode: null, physicalMiB: 0, maxRamMiB: 8192, download: null, clientStarting: false } };
}
export type LogAnchor = { id: number; offset: number };
export function logAnchor(lines: readonly Pick<LogLine, 'id'>[], scrollTop: number): LogAnchor | null {
  const index = Math.min(lines.length - 1, Math.max(0, Math.floor(scrollTop / 26)));
  return index < 0 ? null : { id: lines[index].id, offset: Math.max(0, scrollTop) % 26 };
}
export function resolveLogAnchor(lines: readonly Pick<LogLine, 'id'>[], anchor: LogAnchor | null) {
  if (!anchor || !lines.length) return { scrollTop: 0, discarded: false };
  const index = lines.findIndex(line => line.id >= anchor.id);
  const discarded = lines[0].id > anchor.id;
  return { scrollTop: discarded ? 0 : (index < 0 ? lines.length - 1 : index) * 26 + anchor.offset, discarded };
}
// available = main content box after fixed siblings/gaps; reserve a scrollable main view.
export function consoleBounds(available: number, scale: number, chrome = 45) {
  const reserve = Math.ceil(160 * scale / 100);
  const max = Math.max(0, Math.min(460, Math.floor(available - reserve - chrome)));
  return { min: Math.min(180, max), max, reserve, canOpen: max >= 122 };
}
export function visibleRange(scrollTop: number, viewport: number, count: number, rowHeight = 26) {
  const start = Math.max(0, Math.floor(scrollTop / rowHeight) - 5);
  return { start, end: Math.min(count, Math.ceil((scrollTop + viewport) / rowHeight) + 5) };
}
export function redact(text: string) {
  return text.replace(/(?:Bearer\s+)[\w.\-]+/gi, 'Bearer [REDACTED]')
    .replace(/((?:access[_-]?token|refresh[_-]?token|authorization)["']?\s*[:=]\s*["']?)[^\s"',;]+/gi, '$1[REDACTED]')
    .replace(/[A-Z]:\\Users\\[^\\\s]+/gi, 'C:\\Users\\[USER]')
    .replace(/\/(?:Users|home)\/[^/\s]+/g, '/home/[USER]');
}
