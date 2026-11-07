import { describe, expect, test } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { App } from '../src/App';
import { de, dictionaries, en, scenarioKey, translator } from '../src/copy';
import { initialState, reducer, scenarios, type Locale, type Theme, type View } from '../src/model';
import { themes } from '../src/tokens';
import { allowsPermission, windowOptions } from '../electron/window-contract.cts';

const allThemes = Object.keys(themes) as Theme[];
const locales: Locale[] = ['de', 'en'];
const views: View[] = ['launch', 'options', 'settings', 'update'];
describe('noninteractive React server output — no browser or desktop', () => {
  for (const locale of locales) for (const theme of allThemes) for (const view of views) {
    test(`${locale}/${theme}/${view} has a complete labelled prototype surface`, () => {
      const state = initialState(); state.view = view; state.preferences = { ...state.preferences, locale, theme };
      const html = renderToStaticMarkup(<App seed={state} />);
      const t = translator(locale);
      expect(html).toContain(`<h1>${t(view)}</h1>`);
      expect(html).toContain(t('mockNotice'));
      expect(html).toContain(`data-theme="${theme}"`);
      expect(html).toContain('aria-current="page"');
      expect(html).not.toContain('undefined');
      expect(html).not.toContain('NaN');
      expect(/(?:src|href)="https?:\/\//.test(html)).toBe(false);
    });
  }
  for (const locale of locales) for (const scenario of scenarios) {
    test(`${locale}/${scenario} presents its state without starting anything`, () => {
      const state = initialState(scenario); state.preferences.locale = locale;
      const html = renderToStaticMarkup(<App seed={state} />);
      const content = html.replace(/<select\b[\s\S]*?<\/select>/g, '');
      expect(content).toContain(translator(locale)(scenario === 'first-run' ? 'setupTitle' : scenario === 'empty' ? 'emptyTitle' : scenarioKey[scenario]));
      expect(html).toContain(translator(locale)('mockNotice'));
      if (scenario === 'build' || scenario === 'verify') expect(html).toContain('<progress max="100"');
    });
  }
  test('first build without an installed version still exposes progress', () => {
    const state = reducer(initialState('empty'), { type: 'build', play: false });
    const html = renderToStaticMarkup(<App seed={state} />);
    expect(html).toContain('aria-busy="true"');
    expect(html).toContain('Vorbereiten');
  });
  test('offline recovery check remains enabled after the client ended, while busy/running guards remain', () => {
    for (const locale of locales) for (const view of ['launch', 'update'] as const) {
      const state = initialState('offline'); state.view = view; state.preferences.locale = locale;
      const label = translator(locale)('retryConnection');
      const button = (html: string) => [...html.matchAll(/<button\b([^>]*)>([\s\S]*?)<\/button>/g)].find(match => match[2].includes(label));
      const ready = button(renderToStaticMarkup(<App seed={state} />)); expect(ready).toBeTruthy(); expect(ready![1]).not.toContain('disabled');
      state.status = 'download'; expect(button(renderToStaticMarkup(<App seed={state} />))![1]).toContain('disabled');
      state.status = 'running'; state.runningBuild = state.selected;
      const running = button(renderToStaticMarkup(<App seed={state} />)); if (view === 'launch') expect(running).toBeUndefined(); else expect(running![1]).toContain('disabled');
    }
  });
  test('failed or cancelled first build exposes its simulation cause and retry without claiming a playable version', () => {
    for (const locale of locales) for (const type of ['fail', 'cancel'] as const) {
      let state = reducer(initialState('empty'), { type: 'build', play: false });
      state = reducer(state, { type }); state.preferences.locale = locale;
      const t = translator(locale);
      const html = renderToStaticMarkup(<App seed={state} />).replace(/<select\b[\s\S]*?<\/select>/g, '');
      expect(html).toContain(t(type === 'fail' ? 'firstBuildError' : 'firstBuildCancelled'));
      expect(html).toContain(`role="${type === 'fail' ? 'alert' : 'status'}"`);
      expect(html).toContain(t('retry'));
      expect(html).not.toContain(t('launchButton'));
      expect(state.builds).toHaveLength(0);
      expect(reducer(state, { type: 'build', play: false }).status).toBe('prepare');
    }
  });
  test('console remains part of every view and is hidden outside launch', () => {
    for (const view of views) {
      const state = initialState(); state.view = view;
      const html = renderToStaticMarkup(<App seed={state} />);
      expect(html).toContain('id="console-content"');
      expect(/class="console" hidden=""/.test(html)).toBe(view !== 'launch');
    }
  });
  test('failed update gives recovery instructions outside the fixture picker', () => {
    const state = initialState('error'); state.view = 'update';
    const html = renderToStaticMarkup(<App seed={state} />);
    expect(html).toContain('role="alert"');
    expect(html).toContain(translator('de')('errorHint'));
    expect(html).toContain(translator('de')('retry'));
  });
  test('only download exposes a measured percentage in the build pipeline', () => {
    expect(renderToStaticMarkup(<App seed={initialState('download')} />)).toContain('value="37"');
    expect(renderToStaticMarkup(<App seed={initialState('build')} />)).not.toContain('<progress max="100" value=');
  });
  test('large log buffer does not create thousands of DOM rows', () => {
    const state = reducer(initialState(), { type: 'append', count: 5000 });
    const html = renderToStaticMarkup(<App seed={state} />);
    expect((html.match(/class="log-line /g) ?? []).length).toBeLessThan(20);
    expect(html).toContain('aria-valuemin="180"');
  });
  test.each(locales)('%s protected close dialog contains all three choices', locale => {
    const state = initialState('running'); state.preferences.locale = locale; const t = translator(locale);
    const html = renderToStaticMarkup(<App seed={state} initialModal={{ kind: 'close' }} />);
    for (const key of ['minimize', 'quitBoth', 'stay', 'closeSimulation'] as const) expect(html).toContain(t(key));
    expect(html).toContain('<dialog');
  });
  test('report preview contains no example secret or personal path', () => {
    const html = renderToStaticMarkup(<App seed={initialState()} initialModal={{ kind: 'report' }} />);
    expect(html).not.toContain('synthetic-example');
    expect(html).not.toContain('ExamplePlayer');
    expect(html).toContain('[REDACTED]');
    expect(html).toContain('GitHub-Issue-Vorschau');
  });
  test('locale dictionaries are complete and non-empty', () => {
    expect(Object.keys(de).sort()).toEqual(Object.keys(en).sort());
    for (const dictionary of Object.values(dictionaries)) for (const value of Object.values(dictionary)) expect(value.trim().length).toBeGreaterThan(0);
  });
  test('Electron shell preserves native frame and restricts renderer capability', () => {
    const options = windowOptions('/prototype/preload.cjs');
    expect([options.width, options.height, options.minWidth, options.minHeight]).toEqual([1180, 780, 960, 640]);
    expect(options.frame).toBe(true);
    expect(options.webPreferences).toMatchObject({ nodeIntegration: false, contextIsolation: true, sandbox: true, webviewTag: false });
  });
  test('only the local prototype can write sanitized clipboard text', () => {
    const entry = 'file:///prototype/index.html';
    expect(allowsPermission('clipboard-sanitized-write', entry, entry)).toBe(true);
    expect(allowsPermission('clipboard-read', entry, entry)).toBe(false);
    expect(allowsPermission('camera', entry, entry)).toBe(false);
    expect(allowsPermission('clipboard-sanitized-write', 'https://example.com', entry)).toBe(false);
  });
});

function luminance(hex: string) {
  const rgb = hex.slice(1).match(/../g)!.map(component => Number.parseInt(component, 16) / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4);
  return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
}
function contrast(a: string, b: string) { const values = [luminance(a), luminance(b)].sort((x, y) => y - x); return (values[0] + .05) / (values[1] + .05); }
describe('token contrast arithmetic, not rendered contrast certification', () => {
  test.each(allThemes)('%s normal text and primary endpoints meet 4.5:1', theme => {
    const tokens = themes[theme];
    for (const surface of [tokens.background, tokens.sidebar, tokens.panel, tokens.raised]) {
      expect(contrast(tokens.text, surface)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(tokens.muted, surface)).toBeGreaterThanOrEqual(4.5);
    }
    for (const endpoint of [tokens.accent, tokens.accentEnd]) expect(contrast(tokens.onAccent, endpoint)).toBeGreaterThanOrEqual(4.5);
  });
});
