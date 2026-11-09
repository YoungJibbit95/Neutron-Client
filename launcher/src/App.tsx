import { lazy, Suspense, useEffect, useId, useLayoutEffect, useMemo, useReducer, useRef, useState, type CSSProperties, type Dispatch, type ReactNode } from 'react';
import { ArrowDown, ArrowRight, Check, ChevronDown, ChevronUp, Copy, Download, FileDown, FolderOpen, GitCommitHorizontal, Globe, Info, Monitor, Orbit, Play, RefreshCw, Search, Settings2, ShieldCheck, SlidersHorizontal, Square, Terminal, Trash2, TriangleAlert, WifiOff, X } from 'lucide-react';
import { formatDate, scenarioKey, translator, type CopyKey } from './copy';
import { canInstall, canLaunch, consoleBounds, liveInitialState, isBusy, logAnchor, phases, redact, reducer, recommendedRam, resolveLogAnchor, scenarios, selectedBuild, selectedRelease, validateOptions, visibleRange, type Action, type Build, type Level, type LogAnchor, type LogSource, type Options, type State, type Theme, type View } from './model';
import type { Bridge, Command, Snapshot } from '../electron/contracts.cts';
import { themes, themeStyle } from './tokens';

const LiquidGlass = lazy(() => import('./LiquidGlass'));

type Translate = ReturnType<typeof translator>;
type UI = { state: State; dispatch: Dispatch<Action>; t: Translate };
type Modal = { kind: 'remove'; build: Build } | { kind: 'close' } | { kind: 'report' } | null;
const viewIcons = { launch: Play, options: SlidersHorizontal, settings: Settings2, update: Download };
const themeNames: Record<Theme, string> = { sunset: 'Sunset', ocean: 'Ocean', violet: 'Violet', graphite: 'Graphite' };
declare global { interface Window { neutron?: Bridge } }

function Status({ state, t }: Pick<UI, 'state' | 't'>) {
  const Icon = state.status === 'error' || state.status === 'crash' ? TriangleAlert : state.status === 'offline' ? WifiOff : isBusy(state) ? RefreshCw : Check;
  return <span className={`status status-${state.status}`}><Icon size={14} aria-hidden="true" />{t(scenarioKey[state.status])}</span>;
}
function Primary({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button type="button" className="button button-primary" {...props}>{children}</button>;
}
function Secondary({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button type="button" className="button button-secondary" {...props}>{children}</button>;
}
function Hint({ children, danger = false }: { children: ReactNode; danger?: boolean }) {
  return <p className={danger ? 'field-error' : 'hint'} role={danger ? 'alert' : undefined}>{children}</p>;
}
function Pipeline({ state, dispatch, t }: UI) {
  if (state.runtime?.clientStarting) return <section className="pipeline" aria-busy="true" aria-label={t('startingClient')}><div className="section-heading"><h2>{t('startingClient')}</h2><button className="text-button" onClick={() => dispatch({ type: 'stop' })}>{t('cancel')}</button></div><progress max={100} aria-label={t('startingClient')} /></section>;
  const index = phases.indexOf(state.status as typeof phases[number]);
  return <section className="pipeline" aria-label={t(state.status as CopyKey)} aria-busy="true">
    <div className="section-heading"><h2>{t(state.status as CopyKey)}</h2><button className="text-button" onClick={() => dispatch({ type: 'cancel' })}>{t('cancel')}</button></div>
    <p className="hint">{t('progressHint')}</p>
    <ol className="phase-list">{[...phases, 'ready' as const].map((phase, i) => <li key={phase} data-phase={i < index ? 'done' : i === index ? 'active' : 'waiting'} aria-current={i === index ? 'step' : undefined}><span className="phase-mark">{i < index ? <Check size={12} aria-hidden="true" /> : <span />}</span>{t(phase === 'ready' ? 'done' : phase)}</li>)}</ol>
    <progress max={100} value={state.runtime ? state.status === 'download' && state.runtime.download?.total ? state.runtime.download.received / state.runtime.download.total * 100 : undefined : state.status === 'download' ? 37 : undefined} aria-label={t(state.status as CopyKey)} />
    <span className="hint">{state.runtime?.download ? `${(state.runtime.download.received / 1048576).toFixed(1)} MiB${state.runtime.download.total ? ' / ' + (state.runtime.download.total / 1048576).toFixed(1) + ' MiB' : ''}` : t(state.status === 'download' ? 'downloadProgress' : 'indeterminate')}</span>
  </section>;
}

function LaunchView({ state, dispatch, t, onReport }: UI & { onReport: () => void }) {
  const build = selectedBuild(state);
  const errors = validateOptions(state.options);
  const latest = selectedRelease(state);
  if (state.status === 'first-run' || (state.runtime && !state.setupComplete && !isBusy(state))) return <section className="setup-panel surface">
    <div className="setup-symbol"><Orbit size={34} strokeWidth={1.3} aria-hidden="true" /></div>
    <h2>{t('setupTitle')}</h2><p className="intro">{t('setupIntro')}</p>
    <label className="field-label" htmlFor="setup-path">{t('storage')}</label>
    <div className="input-action"><input id="setup-path" value={state.options.gameDir} onChange={event => dispatch({ type: 'options', patch: { gameDir: event.target.value } })} aria-invalid={errors.includes('directory')} /><Secondary onClick={() => dispatch(state.runtime ? { type: 'browse', kind: 'game' } : { type: 'options', patch: { gameDir: 'D:\\Games\\Neutron Preview' } })}><FolderOpen size={16} />{t('exampleFolder')}</Secondary></div>
    {errors.includes('directory') && <Hint danger>{t('errorDirectory')}</Hint>}
    <h3>{t('setupDownloads')}</h3>
    <ul className="download-list"><li><span>{t('jdkDownload')}</span><span>~102 MiB</span></li><li><span>{t('clientDownload')}</span><span>{latest ? Math.ceil((latest.jar.size + latest.json.size) / 1048576) + ' MiB' : '—'}</span></li><li><span>{t('assetDownload')}</span><span>~145 MiB</span></li></ul>
    <Hint>{t('setupEstimate')}</Hint><Primary disabled={errors.includes('directory') || isBusy(state) || !!state.runningBuild} onClick={() => dispatch({ type: 'setup' })}>{t('setupButton')}<ArrowRight size={18} /></Primary>
  </section>;
  if (!build && isBusy(state)) return <Pipeline state={state} dispatch={dispatch} t={t} />;
  if (!build) return <section className="empty-panel surface"><div className="setup-symbol"><GitCommitHorizontal size={32} aria-hidden="true" /></div><h2>{t('emptyTitle')}</h2><p className="intro">{t('emptyHint')}</p>{(state.status === 'error' || state.status === 'cancelled') && <p className={`notice notice-${state.status}`} role={state.status === 'error' ? 'alert' : 'status'}>{t(state.status === 'error' ? 'firstInstallError' : 'firstInstallCancelled')}</p>}<Primary disabled={!canInstall(state)} onClick={() => dispatch({ type: 'install', play: false })}><Download size={18} />{t(state.status === 'error' || state.status === 'cancelled' ? 'retry' : 'downloadLatest')}</Primary></section>;
  const notice: Partial<Record<State['status'], CopyKey>> = { offline: 'offlineHint', update: 'updateHint', cancelled: 'cancelHint', error: 'errorHint', crash: 'crashHint' };
  return <>
    <section className="launch-hero surface">
      <div className="hero-heading"><div><h2>{build.version ? 'Neutron ' + build.version : t('legacyVersion')}{!build.version && <code> · {build.commit.slice(0, 7)}</code>}</h2><p>{t('minecraft')}</p></div><Status state={state} t={t} /></div>
      <dl className="build-metadata"><div><dt>{t('installedOn')}</dt><dd>{formatDate(build.date, state.preferences.locale)}</dd></div><div><dt>{t('source')}</dt><dd>{build.releaseTag ? 'GitHub Release' : t('localInstallation')}{build.releaseTag && <code>{build.releaseTag}</code>}</dd></div><div><dt>{t('localReady')}</dt><dd><ShieldCheck size={14} aria-hidden="true" />{build.sizeMiB} MiB</dd></div></dl>
      <div className="launch-actions"><Primary disabled={!canLaunch(state)} onClick={() => dispatch({ type: 'launch' })}><Play size={21} fill="currentColor" aria-hidden="true" />{t(state.runtime?.clientStarting ? 'startingClient' : state.runningBuild ? 'running' : state.status === 'crash' ? 'restart' : 'launchButton')}</Primary>{state.runningBuild ? <Secondary onClick={() => dispatch({ type: 'stop' })}><Square size={16} />{t('stop')}</Secondary> : <Secondary disabled={isBusy(state)} onClick={() => dispatch(state.status === 'update' ? { type: 'view', view: 'update' } : { type: 'check' })}><RefreshCw size={16} />{t(!state.online ? 'retryConnection' : state.status === 'update' ? 'viewUpdate' : 'checkUpdate')}</Secondary>}</div>
      <p className="launch-footnote"><Info size={13} aria-hidden="true" />{t('loginHint')}</p>
      {!!errors.length && <p className="field-error" role="alert"><button className="text-button" onClick={() => dispatch({ type: 'view', view: 'options' })}>{t('optionErrors')}</button></p>}
    </section>
    {isBusy(state) && <Pipeline state={state} dispatch={dispatch} t={t} />}
    {notice[state.status] && <section className={`notice notice-${state.status}`} role={state.status === 'error' || state.status === 'crash' ? 'alert' : 'status'}><div><span className="notice-icon">{state.status === 'error' || state.status === 'crash' ? <TriangleAlert size={18} /> : state.status === 'offline' ? <WifiOff size={18} /> : <Info size={18} />}</span><p>{t(notice[state.status]!)}{state.status === 'crash' && <span className="muted"> · {t('exitCode')} {state.runtime?.exitCode ?? '−1'}</span>}</p></div>{(state.status === 'error' || state.status === 'crash') && <button className="text-button" onClick={onReport}>{t('showReport')}<ArrowRight size={14} /></button>}</section>}
  </>;
}

const errorKeys: Record<string, CopyKey> = { ram: 'errorRam', directory: 'errorDirectory', java: 'errorJava', window: 'errorWindow', heap: 'errorHeap' };
function OptionsView({ state, dispatch, t }: UI) {
  const options = state.options;
  const errors = validateOptions(options);
  const physical = state.runtime?.physicalMiB ?? 16384;
  const maximum = state.runtime?.maxRamMiB ?? 8192;
  const recommendation = recommendedRam(physical);
  const set = (patch: Partial<Options>) => dispatch({ type: 'options', patch });
  return <div className="form-stack">
    <section className="settings-section"><div className="section-heading"><h2>{t('memory')}</h2><button className="text-button" onClick={() => set({ ramMiB: recommendation })}>{t('recommended')}: {recommendation / 1024} GiB</button></div><p className="hint" id="ram-hint">{t('memoryHint')}{state.runtime && ` (${physical} MiB · max ${maximum} MiB)`}</p><div className="range-with-number"><input aria-label={t('memory')} type="range" min={1024} max={maximum} step={256} value={Math.max(1024, Math.min(maximum, options.ramMiB))} onChange={event => set({ ramMiB: Number(event.target.value) })} /><div className="number-unit"><input aria-label={`${t('memory')} (MiB)`} aria-describedby="ram-hint" aria-invalid={errors.includes('ram')} type="number" min={1024} max={maximum} step={256} value={options.ramMiB} onChange={event => set({ ramMiB: event.target.valueAsNumber || 0 })} /><span>MiB</span></div></div>{errors.includes('ram') && <Hint danger>{t('errorRam')}</Hint>}</section>
    <section className="settings-section"><h2>{t('directory')}</h2><p className="hint" id="dir-hint">{t('directoryHint')}</p><div className="input-action"><input aria-label={t('directory')} aria-describedby="dir-hint" aria-invalid={errors.includes('directory')} value={options.gameDir} onChange={event => set({ gameDir: event.target.value })} /><Secondary onClick={() => state.runtime ? dispatch({ type: 'browse', kind: 'game' }) : set({ gameDir: 'D:\\Games\\Neutron Preview' })}><FolderOpen size={16} />{t('exampleFolder')}</Secondary></div>{errors.includes('directory') && <Hint danger>{t('errorDirectory')}</Hint>}</section>
    <section className="settings-section"><div className="section-heading"><h2>{t('java')}</h2><span className="quiet-tag">{t('javaHint')}</span></div><label className="sr-only" htmlFor="java-mode">{t('java')}</label><select id="java-mode" value={options.java} onChange={event => set({ java: event.target.value as Options['java'] })}><option value="managed">{t('managed')}</option><option value="custom">{t('customJava')}</option></select>{options.java === 'custom' && <div className="conditional-field"><label htmlFor="java-path">{t('javaPath')}</label><input id="java-path" placeholder="D:\Runtimes\Java8\bin\java.exe" value={options.javaPath} aria-invalid={errors.includes('java')} onChange={event => set({ javaPath: event.target.value })} />{state.runtime && <Secondary onClick={() => dispatch({ type: 'browse', kind: 'java' })}><FolderOpen size={16} />{t('browse')}</Secondary>}{errors.includes('java') && <Hint danger>{t('errorJava')}</Hint>}</div>}</section>
    <section className="settings-section"><h2>{t('display')}</h2><div className="window-options"><label>{t('width')}<input type="number" min={640} max={7680} disabled={options.fullscreen} value={options.width} aria-invalid={errors.includes('window')} onChange={event => set({ width: event.target.valueAsNumber || 0 })} /></label><span aria-hidden="true" className="dimension-times">×</span><label>{t('height')}<input type="number" min={480} max={4320} disabled={options.fullscreen} value={options.height} aria-invalid={errors.includes('window')} onChange={event => set({ height: event.target.valueAsNumber || 0 })} /></label><label className="check-label"><input type="checkbox" checked={options.fullscreen} onChange={event => set({ fullscreen: event.target.checked })} />{t('fullscreen')}</label></div>{errors.includes('window') && <Hint danger>{t('errorWindow')}</Hint>}</section>
    <details className="advanced settings-section"><summary>{t('advanced')}<ChevronDown size={16} aria-hidden="true" /></summary><label htmlFor="jvm-arguments">{t('jvmArgs')}</label><input id="jvm-arguments" className="mono" value={options.jvmArgs} placeholder="-XX:+UseG1GC" aria-describedby="jvm-hint" aria-invalid={errors.includes('heap')} onChange={event => set({ jvmArgs: event.target.value })} /><p className="hint" id="jvm-hint">{t('jvmHint')}</p>{errors.includes('heap') && <Hint danger>{t('errorHeap')}</Hint>}</details>
    {!!errors.length && <ul className="validation-summary" aria-label={t('optionErrors')}>{errors.map(error => <li key={error}>{t(errorKeys[error])}</li>)}</ul>}
    <p className="session-note"><Info size={14} aria-hidden="true" />{t('savedMemory')}</p>
  </div>;
}

function SettingsView({ state, dispatch, t }: UI) {
  const preferences = state.preferences;
  return <div className="form-stack">
    <section className="settings-section theme-section"><h2>{t('appearance')}</h2><p className="hint">{t('themeHint')}</p><div className="theme-grid" role="radiogroup" aria-label={t('appearance')}>{(Object.keys(themeNames) as Theme[]).map(theme => <label key={theme} className={`theme-choice theme-preview-${theme}`} style={{ '--preview-start': themes[theme].accent, '--preview-end': themes[theme].accentEnd } as CSSProperties} data-selected={preferences.theme === theme}><input className="sr-only" type="radio" name="theme" value={theme} checked={preferences.theme === theme} onChange={() => dispatch({ type: 'preferences', patch: { theme } })} /><span className="mini-preview" aria-hidden="true"><span className="mini-sidebar" /><span className="mini-content"><span /><span /><span className="mini-action" /></span></span><span className="theme-name">{themeNames[theme]}{preferences.theme === theme && <Check size={15} aria-hidden="true" />}</span></label>)}</div></section>
    <section className="settings-section setting-row"><div><label className="section-label" htmlFor="language">{t('language')}</label><p className="hint">{t('languageHint')}</p></div><select id="language" value={preferences.locale} onChange={event => dispatch({ type: 'preferences', patch: { locale: event.target.value as 'de' | 'en' } })}><option value="de">Deutsch</option><option value="en">English</option></select></section>
    <section className="settings-section setting-row"><label className="section-label" htmlFor="ui-scale">{t('scale')}</label><select id="ui-scale" value={preferences.scale} onChange={event => dispatch({ type: 'preferences', patch: { scale: Number(event.target.value) } })}>{[90, 100, 110, 125].map(value => <option key={value} value={value}>{value}%</option>)}</select></section>
    <section className="settings-section"><div className="section-heading"><label className="section-label" htmlFor="effect-intensity">{t('effects')}</label><output htmlFor="effect-intensity" className="value-label">{preferences.effects}%</output></div><p className="hint">{t('effectsHint')}</p><input id="effect-intensity" type="range" min={0} max={100} value={preferences.effects} onChange={event => dispatch({ type: 'preferences', patch: { effects: Number(event.target.value) } })} /></section>
    <section className="settings-section setting-row"><div><label className="section-label" htmlFor="reduce-motion">{t('reduced')}</label><p className="hint">{t('reducedHint')}</p></div><input id="reduce-motion" className="switch" type="checkbox" role="switch" checked={preferences.reducedMotion} onChange={event => dispatch({ type: 'preferences', patch: { reducedMotion: event.target.checked } })} /></section><p className="session-note"><Info size={14} aria-hidden="true" />{t('savedMemory')}</p>
  </div>;
}

function UpdateView({ state, dispatch, t, onRemove }: UI & { onRemove: (build: Build) => void }) {
  const [requestedTag, setRequestedTag] = useState('');
  // The empty choice follows the newest stable release as the catalog refreshes.
  // An explicit older/pre-release choice stays selected while it remains available.
  const choice = state.releases.some(release => release.tag === requestedTag) ? requestedTag : '';
  const release = selectedRelease(state, choice);
  const install = (play: boolean) => dispatch({ type: 'install', ...(choice ? { tag: choice } : {}), play });
  const allowed = canInstall(state, choice);
  const optionErrors = validateOptions(state.options);
  return <div className="update-stack">
    <section className="repository-panel surface">
      <div className="section-heading"><h2>{t('github')}</h2>{state.status === 'update' ? <Status state={state} t={t} /> : <span className="quiet-tag">{t('releaseFiles')}</span>}</div>
      <p className="repo-name">YoungJibbit95 / <strong>Neutron-Client</strong></p>
      <div className="repo-summary"><span>{t('githubHint')}</span>{state.runtime?.lastChecked && <span>{t('checked')}: {formatDate(state.runtime.lastChecked, state.preferences.locale)}</span>}</div>
      {state.releases.length ? <>
        <div className="release-picker"><label className="field-label" htmlFor="release-choice">{t('releaseChoice')}</label><select id="release-choice" value={choice} disabled={isBusy(state) || !!state.runningBuild} onChange={event => setRequestedTag(event.target.value)}>
          <option value="">{t(selectedRelease(state)?.prerelease ? 'newestPrerelease' : 'newestStable')}{state.latestRelease ? ' · ' + state.latestRelease : ''}</option>
          {state.releases.map(item => <option key={item.id} value={item.tag}>{item.tag} · {t(item.prerelease ? 'prerelease' : 'stable')} · {formatDate(item.publishedAt, state.preferences.locale)}</option>)}
        </select><p className="hint">{t('catalogCount', { count: state.releases.length })}</p></div>
        {release && <div className="release-detail"><div><strong>Neutron {release.version}</strong><span className="quiet-tag">{t(release.prerelease ? 'prerelease' : 'stable')}</span></div><p>{t('publishedOn')}: {formatDate(release.publishedAt, state.preferences.locale)} · {Math.ceil((release.jar.size + release.json.size) / 1048576)} MiB · {t('releaseFiles')}</p></div>}
      </> : <p className="empty-catalog" role="status">{t('noReleases')}</p>}
      <div className="update-actions">
        <Primary disabled={!allowed} title={!state.online ? t('offlineHint') : state.runningBuild ? t('protected') : undefined} onClick={() => install(false)}><Download size={17} />{t(choice ? 'downloadSelected' : 'downloadLatest')}</Primary>
        <Secondary disabled={!allowed || !!optionErrors.length} title={optionErrors.length ? t('optionErrors') : undefined} onClick={() => install(true)}><Play size={16} />{t('downloadPlay')}</Secondary>
        <button className={state.online ? 'icon-button' : 'button button-secondary'} disabled={!!state.runningBuild || isBusy(state)} aria-label={t(!state.online ? 'retryConnection' : 'checkUpdate')} title={t(!state.online ? 'retryConnection' : 'checkUpdate')} onClick={() => dispatch({ type: 'check' })}><RefreshCw size={18} />{!state.online && t('retryConnection')}</button>
      </div>
      {!state.setupComplete && <div className="setup-required"><Hint>{t('setupRequired')}</Hint><button className="text-button" disabled={isBusy(state) || !!state.runningBuild} onClick={() => dispatch({ type: 'view', view: 'launch' })}>{t('firstRun')}<ArrowRight size={14} /></button></div>}
      {!state.online && <Hint>{t('offlineHint')}</Hint>}
    </section>
    {isBusy(state) && <Pipeline state={state} dispatch={dispatch} t={t} />}
    {(state.status === 'error' || state.status === 'cancelled') && <section className={`notice notice-${state.status}`} role={state.status === 'error' ? 'alert' : 'status'}><div><TriangleAlert size={18} aria-hidden="true" /><p>{t(state.status === 'error' ? 'errorHint' : 'cancelHint')}</p></div><button className="text-button" disabled={!allowed} onClick={() => install(false)}>{t('retry')}<ArrowRight size={14} /></button></section>}
    <section className="build-list"><div className="section-heading"><div><h2>{t('installed')}</h2><p className="hint">{t('installedHint')}</p></div><span className="count-label">{state.builds.length}</span></div>{!state.builds.length ? <p className="empty-build-list">{t('noBuilds')}</p> : <div className="table-scroll"><table><thead><tr><th>{t('version')}</th><th>{t('installedOn')}</th><th>{t('size')}</th><th><span className="sr-only">{t('select')}</span></th></tr></thead><tbody>{state.builds.map(build => <tr key={build.id} data-selected={state.selected === build.id}><td><span className="build-name">{build.version ? 'Neutron ' + build.version : t('legacyVersion')}</span><code>{build.releaseTag ?? build.commit.slice(0, 7)}</code></td><td>{formatDate(build.date, state.preferences.locale)}</td><td className="mono">{build.sizeMiB} MiB</td><td><div className="row-actions"><button className={state.selected === build.id ? 'selected-build' : 'text-button'} disabled={state.selected === build.id || !!state.runningBuild || isBusy(state)} onClick={() => dispatch({ type: 'select', id: build.id })}>{state.selected === build.id && <Check size={14} />}{t(state.selected === build.id ? 'current' : 'select')}</button><button className="icon-button" aria-label={`${t('remove')} ${build.releaseTag ?? build.commit.slice(0, 7)}`} title={state.selected === build.id || state.runningBuild === build.id ? t('protected') : t('remove')} disabled={state.selected === build.id || state.runningBuild === build.id || isBusy(state)} onClick={() => onRemove(build)}><Trash2 size={15} /></button></div></td></tr>)}</tbody></table></div>}<p className="hint protection-note"><ShieldCheck size={13} aria-hidden="true" />{t('protected')}</p></section>
  </div>;
}

function Console({ state, t, notify }: Pick<UI, 'state' | 't'> & { notify: (text: string) => void }) {
  const [source, setSource] = useState<LogSource>('client');
  const [level, setLevel] = useState<Level | 'all'>('all');
  const [query, setQuery] = useState('');
  const [preferredHeight, setHeight] = useState(210);
  const [space, setSpace] = useState({ available: 435, chrome: 45 });
  const [resizing, setResizing] = useState(false);
  const [open, setOpen] = useState(true);
  const [scrollTop, setScrollTop] = useState(0);
  const [following, setFollowing] = useState(true);
  const [seenId, setSeenId] = useState(0);
  const [discarded, setDiscarded] = useState(false);
  const anchor = useRef<LogAnchor | null>(null);
  const programmedScroll = useRef<number | null>(null);
  const section = useRef<HTMLElement>(null);
  const viewport = useRef<HTMLDivElement>(null);
  const visible = state.view === 'launch';
  const bounds = consoleBounds(space.available, state.preferences.scale, space.chrome);
  const height = Math.max(bounds.min, Math.min(bounds.max, preferredHeight));
  const expanded = open && bounds.canOpen;
  const viewportHeight = Math.max(26, height - 68 - (discarded ? 28 : 0));
  const timeFormatter = useMemo(() => new Intl.DateTimeFormat(state.preferences.locale === 'de' ? 'de-DE' : 'en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'Europe/Berlin' }), [state.preferences.locale]);
  const lines = useMemo(() => state.logs.filter(line => line.source === source && (level === 'all' || line.level === level) && line.message[state.preferences.locale].toLocaleLowerCase().includes(query.toLocaleLowerCase())), [state.logs, state.preferences.locale, source, level, query]);
  const resolved = resolveLogAnchor(lines, anchor.current);
  const range = visibleRange(following ? scrollTop : resolved.scrollTop, viewportHeight, lines.length);
  const latestId = lines.at(-1)?.id ?? 0;
  const newCount = following ? 0 : lines.filter(line => line.id > seenId).length;
  useLayoutEffect(() => {
    if (!visible || !section.current?.parentElement) return;
    const panel = section.current;
    const main = panel.parentElement!;
    const measure = () => {
      const style = getComputedStyle(main);
      const siblings = Array.from(main.children).filter((element): element is HTMLElement => element instanceof HTMLElement && getComputedStyle(element).display !== 'none' && getComputedStyle(element).position !== 'fixed');
      const fixed = siblings.filter(element => element !== panel && !element.classList.contains('view-content'));
      const available = main.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom) - parseFloat(style.rowGap) * Math.max(0, siblings.length - 1) - fixed.reduce((sum, element) => sum + element.getBoundingClientRect().height, 0);
      const chrome = panel.querySelector('.resize-handle')!.getBoundingClientRect().height + panel.querySelector('.console-heading')!.getBoundingClientRect().height + 2;
      setSpace(previous => previous.available === available && previous.chrome === chrome ? previous : { available, chrome });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(main);
    for (const element of Array.from(main.children)) if (element !== panel && !element.classList.contains('view-content')) observer.observe(element);
    window.addEventListener('resize', measure);
    return () => { observer.disconnect(); window.removeEventListener('resize', measure); };
  }, [visible, state.preferences.scale, state.preferences.locale]);
  useLayoutEffect(() => {
    const element = viewport.current;
    if (!visible || !expanded || !element) return;
    element.scrollTop = following ? Math.max(0, element.scrollHeight - element.clientHeight) : resolved.scrollTop;
    programmedScroll.current = element.scrollTop;
    setScrollTop(element.scrollTop);
    if (!following) {
      anchor.current = logAnchor(lines, element.scrollTop);
      if (resolved.discarded) setDiscarded(true);
    }
  }, [lines, following, visible, expanded, viewportHeight, resolved.scrollTop, resolved.discarded]);
  const resetFilter = () => { anchor.current = null; setFollowing(true); setDiscarded(false); setScrollTop(0); };
  const text = () => redact(lines.map(line => `${line.time} [${line.source}/${line.level.toUpperCase()}] ${line.message[state.preferences.locale]}`).join('\n'));
  const copy = async () => { try { await navigator.clipboard.writeText(text()); notify(t('copied')); } catch { notify(t('copyFailed')); } };
  const exportLogs = () => {
    if (state.runtime && window.neutron) { void window.neutron.command({ type: 'export', source }).then(result => { if (typeof result === 'string' && result) notify(t('exported')); }).catch(error => notify(String(error))); return; }
    const url = URL.createObjectURL(new Blob([text()], { type: 'text/plain;charset=utf-8' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'neutron-prototype-logs.txt'; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 0); notify(t('exported'));
  };
  return <section ref={section} className="console" hidden={!visible} data-open={expanded} data-constrained={preferredHeight > bounds.max} data-resizing={resizing} aria-label={t('console')}>
    <div className="resize-handle" role="separator" aria-label={t('resize')} aria-orientation="horizontal" aria-valuemin={bounds.min} aria-valuemax={bounds.max} aria-valuenow={height} tabIndex={0}
      onKeyDown={event => { if (event.key === 'ArrowUp' || event.key === 'ArrowDown') { event.preventDefault(); setResizing(true); setHeight(Math.max(bounds.min, Math.min(bounds.max, height + (event.key === 'ArrowUp' ? 20 : -20)))); } }} onKeyUp={() => setResizing(false)}
      onPointerDown={event => { const initial = event.clientY; const original = height; setResizing(true); event.currentTarget.setPointerCapture(event.pointerId); event.currentTarget.dataset.start = `${initial},${original}`; }}
      onPointerMove={event => { if (event.currentTarget.hasPointerCapture(event.pointerId)) { const [initial, original] = (event.currentTarget.dataset.start ?? '').split(',').map(Number); if (Number.isFinite(initial)) setHeight(Math.max(bounds.min, Math.min(bounds.max, original + initial - event.clientY))); } }}
      onPointerUp={event => { setResizing(false); if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId); }} onPointerCancel={() => setResizing(false)}><span /></div>
    <div className="console-heading"><h2><Terminal size={15} aria-hidden="true" />{t('console')}</h2><div className="console-actions"><button className="icon-button" aria-label={t('copy')} title={t('copy')} onClick={() => void copy()}><Copy size={15} /></button><button className="icon-button" aria-label={t('export')} title={t('export')} onClick={exportLogs}><FileDown size={15} /></button><button className="icon-button" disabled={!bounds.canOpen} aria-label={t(expanded ? 'collapse' : 'expand')} aria-expanded={expanded} aria-controls="console-content" onClick={() => setOpen(!expanded)}>{expanded ? <ChevronDown size={17} /> : <ChevronUp size={17} />}</button></div></div>
    <div id="console-content" className="console-content" inert={!expanded} style={{ height: expanded ? height : 0 }}>
      <div className="console-toolbar"><div className="segmented" aria-label={t('console')}>{(['client', 'build'] as LogSource[]).map(value => <button key={value} aria-pressed={value === source} onClick={() => { setSource(value); resetFilter(); }}>{t(value === 'client' ? 'clientLogs' : 'installationLogs')}</button>)}</div><div className="log-search"><Search size={14} aria-hidden="true" /><input aria-label={t('search')} placeholder={t('search')} value={query} onChange={event => { setQuery(event.target.value); resetFilter(); }} /></div><select aria-label={t('allLevels')} value={level} onChange={event => { setLevel(event.target.value as Level | 'all'); resetFilter(); }}><option value="all">{t('allLevels')}</option><option value="info">{t('info')}</option><option value="warn">{t('warn')}</option><option value="error">{t('errors')}</option></select></div>
      {discarded && <p className="log-discarded" role="status">{t('olderLogsDiscarded')}</p>}
      <div className="log-viewport" ref={viewport} tabIndex={0} aria-label={`${t('console')}: ${t(source === 'client' ? 'clientLogs' : 'installationLogs')}`} style={{ height: viewportHeight }} onScroll={event => { const element = event.currentTarget; setScrollTop(element.scrollTop); if (!visible || !expanded) return; if (programmedScroll.current !== null && Math.abs(programmedScroll.current - element.scrollTop) < 1) { programmedScroll.current = null; return; } programmedScroll.current = null; anchor.current = logAnchor(lines, element.scrollTop); const bottom = element.scrollHeight - element.scrollTop - element.clientHeight < 12; if (following && !bottom) setSeenId(latestId); setFollowing(bottom); if (bottom) setDiscarded(false); }}>
        {!lines.length ? <p className="no-logs">{t('noLogs')}</p> : <div className="log-spacer" style={{ height: lines.length * 26 }}><div className="log-window" style={{ transform: `translateY(${range.start * 26}px)` }}>{lines.slice(range.start, range.end).map(line => <div className={`log-line log-${line.level}`} key={line.id}><time dateTime={line.time}>{timeFormatter.format(new Date(line.time))}</time><span className="log-level">{line.level.toUpperCase()}</span><span>{line.message[state.preferences.locale]}</span></div>)}</div></div>}
      </div>
      <div className="console-footer"><span className="scroll-status"><span className={following ? 'dot dot-positive' : 'dot'} />{t(following ? 'autoScroll' : 'paused')}</span>{!following && <button className="text-button" onClick={() => { setFollowing(true); setDiscarded(false); }}><ArrowDown size={12} />{newCount ? t('newLines', { count: newCount }) : t('resume')}</button>}<span className="log-count">{lines.length} / 2000</span></div>
    </div>
  </section>;
}

function Dialog({ title, children, onClose, closeLabel }: { title: string; children: ReactNode; onClose: () => void; closeLabel: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const element = ref.current;
    const trigger = document.activeElement;
    if (element && !element.open) element.showModal();
    return () => { if (element?.open) element.close(); if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus(); };
  }, []);
  return <dialog ref={ref} aria-labelledby={titleId} onCancel={event => { event.preventDefault(); onClose(); }} onClick={event => { if (event.target === event.currentTarget) onClose(); }}><div className="dialog-heading"><h2 id={titleId}>{title}</h2><button className="icon-button" aria-label={closeLabel} onClick={onClose}><X size={18} /></button></div>{children}</dialog>;
}

export function App({ seed, initialModal = null }: { seed?: State; initialModal?: Modal }) {
  const demo = seed !== undefined;
  const bridge = !demo && typeof window !== 'undefined' ? window.neutron : undefined;
  const [state, localDispatch] = useReducer(reducer, seed ?? liveInitialState());
  const [modal, setModal] = useState<Modal>(initialModal);
  const [issuePreview, setIssuePreview] = useState(false);
  const [message, setMessage] = useState('');
  const [liveReport, setLiveReport] = useState('');
  const pendingSaves = useRef(0);
  const [systemReduce, setSystemReduce] = useState(false);
  const [animateView, setAnimateView] = useState(false);
  const [inputMode, setInputMode] = useState<'pointer' | 'keyboard'>('pointer');
  const content = useRef<HTMLDivElement>(null);
  const t = translator(state.preferences.locale, !demo);
  const receive = (snapshot: Snapshot) => localDispatch({ type: 'snapshot', snapshot, preserveEdits: pendingSaves.current > 0 });
  const invoke = async (command: Command) => {
    if (!bridge) { setMessage(state.preferences.locale === 'de' ? 'Desktop-Verbindung nicht verfügbar.' : 'Desktop connection unavailable.'); return; }
    try { const result = await bridge.command(command); if (typeof result === 'string') { if (result) setMessage(result); } else receive(result); }
    catch (error) { setMessage(String(error)); }
  };
  const dispatch: Dispatch<Action> = action => {
    if (demo || action.type === 'view') { localDispatch(action); return; }
    if (action.type === 'options' || action.type === 'preferences') {
      localDispatch(action); pendingSaves.current++;
      if (!bridge) { pendingSaves.current--; return; }
      void bridge.command({ type: action.type === 'options' ? 'save-options' : 'save-preferences', patch: action.patch } as Command).then(result => { pendingSaves.current--; if (typeof result !== 'string') receive(result); }).catch(error => { pendingSaves.current--; setMessage(String(error)); });
    } else if (['setup', 'check', 'install', 'launch', 'stop', 'cancel', 'select', 'remove', 'browse'].includes(action.type)) void invoke(action as Command);
  };
  useEffect(() => {
    if (!bridge) return;
    const stop = bridge.subscribe(receive); const stopClose = bridge.onClose(() => setModal({ kind: 'close' }));
    void bridge.snapshot().then(receive).catch(error => setMessage(String(error)));
    return () => { stop(); stopClose(); };
  }, [bridge]);
  useEffect(() => { if (bridge && modal?.kind === 'report') { setLiveReport('…'); void bridge.command({ type: 'report' }).then(value => { if (typeof value === 'string') setLiveReport(value); }).catch(error => setLiveReport(String(error))); } }, [bridge, modal?.kind]);
  useEffect(() => { if (!bridge?.ready) return; const frame = requestAnimationFrame(() => bridge.ready?.()); return () => cancelAnimationFrame(frame); }, [bridge]);
  const reduced = state.preferences.reducedMotion || systemReduce;
  const [glassReady, setGlassReady] = useState(false);
  useEffect(() => { if (reduced || state.preferences.effects === 0) { setGlassReady(false); return; } const timer = setTimeout(() => setGlassReady(true), 300); return () => clearTimeout(timer); }, [reduced, state.preferences.effects]);
  useEffect(() => { const query = window.matchMedia('(prefers-reduced-motion: reduce)'); const change = () => setSystemReduce(query.matches); change(); query.addEventListener('change', change); return () => query.removeEventListener('change', change); }, []);
  useEffect(() => { document.documentElement.lang = state.preferences.locale; }, [state.preferences.locale]);
  useEffect(() => {
    if (!animateView || !content.current?.animate) return;
    const animation = content.current.animate(reduced ? [{ opacity: 0.8 }, { opacity: 1 }] : [{ opacity: 0.7, transform: 'translateY(4px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: reduced ? 80 : 180, easing: 'cubic-bezier(0.23,1,0.32,1)' });
    return () => animation.cancel();
  }, [state.view, animateView, reduced]);
  const ui: UI = { state, dispatch, t };
  const appStyle = { ...themeStyle(state.preferences.theme), '--ui-scale': state.preferences.scale / 100, '--effect': state.preferences.effects / 100 } as CSSProperties;
  const reportText = demo ? redact(`Neutron Launcher — ${t('prototype')}\n${selectedBuild(state)?.releaseTag ?? selectedBuild(state)?.commit.slice(0, 7) ?? '—'}\n${t('exitCode')}: -1 (${t('example')})\n${t('profile')}: C:\\Users\\ExamplePlayer\\Games\\Neutron\naccessToken=synthetic-example\n${t('noCredentials')}`) : liveReport;
  return <div className="app" data-theme={state.preferences.theme} data-reduced-motion={reduced} data-input-mode={inputMode} onKeyDownCapture={() => setInputMode('keyboard')} onPointerDownCapture={() => setInputMode('pointer')} style={appStyle}>
    <a className="skip-link" href="#main-content">{t(state.view)}</a>
    <aside className="sidebar"><div className="brand"><span className="brand-symbol"><Orbit size={25} strokeWidth={1.5} aria-hidden="true" /></span><span>neutron<span className="brand-subtitle">Launcher</span></span></div><nav aria-label="Neutron">{(['launch', 'options', 'settings', 'update'] as View[]).map(view => { const Icon = viewIcons[view]; return <button key={view} aria-current={state.view === view ? 'page' : undefined} onClick={event => { setAnimateView(event.detail > 0); dispatch({ type: 'view', view }); }}><Icon size={18} aria-hidden="true" /><span>{t(view)}</span>{view === 'update' && state.status === 'update' && <span className="nav-indicator" aria-label={t('updateAvailable')} />}</button>; })}</nav><div className="sidebar-bottom"><span className="sidebar-network">{state.online ? <Globe size={14} /> : <WifiOff size={14} />}{t(state.online ? 'online' : 'offlineLabel')}</span><span className="prototype-label">{t('prototype')}</span><code>Launcher {state.launcherVersion}</code></div></aside>
    <main className="main" id="main-content" tabIndex={-1}>{glassReady && !reduced && <Suspense fallback={null}><LiquidGlass color={themes[state.preferences.theme].accent} strength={state.preferences.effects} /></Suspense>}<header className="page-header"><div><h1>{t(state.view)}</h1><p>{t(`${state.view}Intro` as CopyKey)}</p></div><button className="icon-button support-button" aria-label={t('report')} title={t('report')} onClick={() => { setIssuePreview(false); setModal({ kind: 'report' }); }}><Info size={19} /></button></header><div className="prototype-notice"><Info size={14} aria-hidden="true" /><span>{t('mockNotice')}</span></div>
      <div ref={content} className={`view-content view-${state.view}`}>
        {state.runtime?.error && <section className="notice notice-error" role="alert"><p>{state.runtime.error}</p>{!isBusy(state) && !state.runningBuild && <button className="text-button" onClick={() => dispatch({ type: state.setupComplete ? 'check' : 'setup' })}>{t('retry')}</button>}</section>}
        {state.view === 'launch' && <LaunchView {...ui} onReport={() => { setIssuePreview(false); setModal({ kind: 'report' }); }} />}
        {state.view === 'options' && <OptionsView {...ui} />}
        {state.view === 'settings' && <SettingsView {...ui} />}
        {state.view === 'update' && <UpdateView {...ui} onRemove={build => setModal({ kind: 'remove', build })} />}
      </div>
      <Console state={state} t={t} notify={setMessage} />
      {demo && <details className="prototype-controls"><summary>{t('prototypeControls')}<ChevronDown size={13} /></summary><div><label>{t('scenario')}<select value={state.status} onChange={event => dispatch({ type: 'scenario', scenario: event.target.value as State['status'] })}>{scenarios.map(scenario => <option key={scenario} value={scenario}>{t(scenarioKey[scenario])}</option>)}</select></label>{isBusy(state) && <><Secondary onClick={() => dispatch({ type: 'next-phase' })}>{t('nextPhase')}</Secondary><button className="text-button" onClick={() => dispatch({ type: 'fail' })}>{t('simulateError')}</button></>}<Secondary onClick={() => dispatch({ type: 'append', count: 2000 })}>{t('logLoad')}</Secondary><Secondary disabled={!state.runningBuild} onClick={() => setModal({ kind: 'close' })}>{t('simulateClose')}</Secondary></div></details>}
      <div className="feedback" role="status" aria-live="polite">{message}</div>
    </main>
    {modal?.kind === 'remove' && <Dialog title={t('removeTitle')} closeLabel={t('close')} onClose={() => setModal(null)}><p>{t('removeHint')}</p><code className="dialog-commit">{modal.build.releaseTag ?? t('legacyVersion') + ' · ' + modal.build.commit.slice(0, 7)}</code><div className="dialog-actions"><Secondary onClick={() => setModal(null)}>{t('keep')}</Secondary><button className="button button-danger" onClick={() => { dispatch({ type: 'remove', id: modal.build.id }); setModal(null); }}>{t('remove')}</button></div></Dialog>}
    {modal?.kind === 'close' && <Dialog title={t('closeTitle')} closeLabel={t('close')} onClose={() => setModal(null)}><p>{t('closeHint')}</p><Hint>{t('closeSimulation')}</Hint><div className="close-choices"><Secondary onClick={() => { if (demo) setMessage(t('closeSimulation')); else void invoke({ type: 'close', choice: 'minimize' }); setModal(null); }}><Monitor size={16} />{t('minimize')}</Secondary><button className="button button-danger" onClick={() => { if (demo) { dispatch({ type: 'stop' }); setMessage(t('demoStopped')); } else void invoke({ type: 'close', choice: 'quit' }); setModal(null); }}><Square size={16} />{t('quitBoth')}</button><button className="text-button" onClick={() => setModal(null)}>{t('stay')}</button></div></Dialog>}
    {modal?.kind === 'report' && <Dialog title={t(issuePreview ? 'issuePreview' : 'reportTitle')} closeLabel={t('close')} onClose={() => setModal(null)}><p>{t(issuePreview ? 'issueHint' : 'reportHint')}</p><pre className="report-preview">{reportText}</pre><div className="dialog-actions"><Secondary onClick={() => setModal(null)}>{t('close')}</Secondary>{!issuePreview && <Primary onClick={() => setIssuePreview(true)}>{t('issuePreview')}<ArrowRight size={16} /></Primary>}</div></Dialog>}
  </div>;
}
