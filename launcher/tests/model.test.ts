import { describe, expect, test } from 'vitest';
import { canInstall, canLaunch, consoleBounds, fixtureBuilds, initialState, isBusy, liveInitialState, logAnchor, MAX_LOG_LINES, nextFixture, recommendedRam, redact, reducer, resolveLogAnchor, selectedBuild, selectedRelease, validateOptions, visibleRange } from '../src/model';

describe('prototype invariants', () => {
  test('the default follows the latest stable release and an explicit pre-release is selectable', () => {
    const state = initialState();
    expect(state.releases[0].prerelease).toBe(true);
    expect(selectedRelease(state)?.tag).toBe('v0.2.2');
    let installing = reducer(state, { type: 'install', tag: 'v0.3.0-beta.1', play: false });
    expect(installing.installingTag).toBe('v0.3.0-beta.1');
    for (let i = 0; i < 3; i++) installing = reducer(installing, { type: 'next-phase' });
    expect(selectedBuild(installing)?.releaseTag).toBe('v0.3.0-beta.1');
    expect(installing.builds.some(build => build.id === state.selected)).toBe(true);
  });
  test('missing or unknown release choices cannot initiate installation or replace a playable version', () => {
    const state = initialState();
    expect(canInstall(state, 'v99.0.0')).toBe(false);
    expect(reducer(state, { type: 'install', tag: 'v99.0.0', play: true })).toEqual(state);
    const noCatalog = { ...state, releases: [], latestRelease: null };
    expect(canInstall(noCatalog)).toBe(false);
    expect(canLaunch(noCatalog)).toBe(true);
  });
  test('desktop initialization contains no illustrative release catalog or installed versions', () => {
    const state = liveInitialState();
    expect(state.releases).toEqual([]);
    expect(state.latestRelease).toBeNull();
    expect(state.builds).toEqual([]);
    expect(canInstall(state)).toBe(false);
  });
  test('offline installed versions launch without permitting an installation', () => {
    const offline = initialState('offline');
    expect(canLaunch(offline)).toBe(true);
    expect(canInstall(offline)).toBe(false);
    expect(reducer(offline, { type: 'launch' }).online).toBe(false);
  });
  test('first-run cannot be bypassed by the update view', () => {
    let state = initialState('first-run');
    state = reducer(state, { type: 'view', view: 'update' });
    state = reducer(state, { type: 'check' });
    expect(state.status).toBe('first-run');
    expect(canInstall(state)).toBe(false);
    state = reducer(state, { type: 'setup' });
    expect(state.builds).toHaveLength(0);
    expect(state.setupComplete).toBe(false);
    expect(canInstall(state)).toBe(false);
    for (let i = 0; i < 3; i++) state = reducer(state, { type: 'next-phase' });
    expect(state.setupComplete).toBe(true);
    expect(state.builds).toHaveLength(1);
    expect(canInstall(state)).toBe(true);
  });
  test.each(['cancel', 'fail'] as const)('%s keeps the previous playable build and cancels auto-launch', type => {
    const before = initialState();
    const building = reducer(before, { type: 'install', play: true });
    const failed = reducer(building, { type });
    expect(failed.selected).toBe(before.selected);
    expect(failed.builds).toEqual(before.builds);
    expect(failed.autoLaunch).toBe(false);
    expect(canLaunch(failed)).toBe(true);
  });
  test('activation and optional play happen only after verification', () => {
    let state = reducer(initialState(), { type: 'install', play: true });
    for (const status of ['prepare', 'download', 'verify']) {
      expect(state.status).toBe(status);
      expect(state.selected).toBe(fixtureBuilds[0].id);
      expect(state.runningBuild).toBeNull();
      state = reducer(state, { type: 'next-phase' });
    }
    expect(state.selected).toBe(nextFixture.id);
    expect(state.runningBuild).toBe(nextFixture.id);
    expect(state.builds.some(build => build.id === fixtureBuilds[0].id)).toBe(true);
  });
  test('invalid options prevent launch and download-and-play, including mid-installation edits', () => {
    const invalid = reducer(initialState(), { type: 'options', patch: { jvmArgs: '-Xmx99G' } });
    expect(canLaunch(invalid)).toBe(false);
    expect(reducer(invalid, { type: 'install', play: true })).toEqual(invalid);
    let building = reducer(initialState(), { type: 'install', play: true });
    building = reducer(building, { type: 'options', patch: { ramMiB: 0 } });
    for (let i = 0; i < 3; i++) building = reducer(building, { type: 'next-phase' });
    expect(building.status).toBe('ready');
    expect(building.runningBuild).toBeNull();
  });
  test('a running build cannot be switched, removed or reinstalled', () => {
    const running = reducer(initialState(), { type: 'launch' });
    expect(reducer(running, { type: 'remove', id: running.runningBuild! })).toEqual(running);
    expect(reducer(running, { type: 'select', id: fixtureBuilds[1].id })).toEqual(running);
    expect(reducer(running, { type: 'install', play: false })).toEqual(running);
  });
  test('a non-selected older build can be removed without changing the current version', () => {
    const state = reducer(initialState(), { type: 'remove', id: fixtureBuilds[1].id });
    expect(state.builds).toHaveLength(1);
    expect(selectedBuild(state)?.id).toBe(fixtureBuilds[0].id);
  });
  test('log load remains bounded with monotonic stable keys', () => {
    const state = reducer(initialState(), { type: 'append', count: 5000 });
    expect(state.logs).toHaveLength(MAX_LOG_LINES);
    expect(new Set(state.logs.map(line => line.id)).size).toBe(MAX_LOG_LINES);
    expect(state.logs.at(-1)!.id).toBeGreaterThan(5000);
  });
  test('virtualization keeps a bounded visible window under load', () => {
    for (const top of [0, 500, 25000, 51000]) {
      const { start, end } = visibleRange(top, 210, 2000);
      expect(start).toBeGreaterThanOrEqual(0);
      expect(end).toBeLessThanOrEqual(2000);
      expect(end - start).toBeLessThanOrEqual(20);
    }
  });
  test('paused reading follows the same log ID and partial-row offset when the full buffer rotates', () => {
    const full = reducer(initialState(), { type: 'append', count: 2000 });
    const lines = full.logs.filter(line => line.source === 'client');
    const position = 300 * 26 + 9;
    const anchor = logAnchor(lines, position)!;
    const rotated = reducer(full, { type: 'append', count: 120 });
    const available = rotated.logs.filter(line => line.source === 'client');
    const restored = resolveLogAnchor(available, anchor);
    expect(restored.discarded).toBe(false);
    expect(restored.scrollTop).toBeLessThan(position);
    expect(logAnchor(available, restored.scrollTop)).toEqual(anchor);
  });
  test('evicted paused anchor moves to the oldest remaining row and reports discarded output', () => {
    const full = reducer(initialState(), { type: 'append', count: 2000 });
    const anchor = logAnchor(full.logs, 26 + 11);
    const rotated = reducer(full, { type: 'append', count: 2000 });
    expect(resolveLogAnchor(rotated.logs, anchor)).toEqual({ scrollTop: 0, discarded: true });
    expect(logAnchor(rotated.logs, 0)).toEqual({ id: rotated.logs[0].id, offset: 0 });
    expect(resolveLogAnchor([], anchor)).toEqual({ scrollTop: 0, discarded: false });
  });
  test('console budget preserves the main view on small windows, larger UI scales and expanded controls', () => {
    // 640px window, measured padding/headers/controls/gaps take 250px.
    const small = consoleBounds(640 - 250, 100);
    expect(small).toEqual({ min: 180, max: 185, reserve: 160, canOpen: true });
    const scaled = consoleBounds(640 - 280, 125, 49);
    expect(scaled.max).toBe(111);
    expect(scaled.canOpen).toBe(false);
    for (const [available, scale, chrome] of [[390, 100, 45], [360, 125, 49], [300, 150, 52], [1000, 100, 45]]) {
      const bounds = consoleBounds(available, scale, chrome);
      expect(bounds.max + bounds.reserve + chrome).toBeLessThanOrEqual(available);
      expect(bounds.max).toBeLessThanOrEqual(460);
      expect(bounds.min).toBeLessThanOrEqual(bounds.max);
    }
    expect(consoleBounds(100, 150).canOpen).toBe(false);
  });
});
describe('launch options and diagnosis', () => {
  test.each(['-Xmx8G', '-Xms4G', '"-Xmx8G"', '-Dfoo=bar -XX:MaxRAMPercentage=95'])('rejects conflicting memory options: %s', jvmArgs => {
    expect(validateOptions({ ...initialState().options, jvmArgs })).toContain('heap');
  });
  test('accepts paths with spaces/umlauts, flags invalid Java and dimensions', () => {
    const options = { ...initialState().options, gameDir: 'F:\\Neutron Prüfung', java: 'custom' as const, javaPath: '/opt/Java 8/bin/java' };
    expect(validateOptions(options)).toEqual([]);
    expect(validateOptions({ ...options, javaPath: 'java', width: 0 })).toEqual(['java', 'window']);
  });
  test('mock RAM recommendation respects the approved 1–4 GiB default range', () => {
    expect(recommendedRam(4096)).toBe(1024);
    expect(recommendedRam(8192)).toBe(2048);
    expect(recommendedRam(32768)).toBe(4096);
  });
  test('diagnosis removes recognizable tokens and personal path segments', () => {
    const text = redact('accessToken=super-secret refresh_token="another-secret" Bearer abc.def.ghi C:\\Users\\Alice\\Games /home/bob/game /Users/Claire/Library');
    for (const secret of ['super-secret', 'another-secret', 'abc.def.ghi', 'Alice', 'bob', 'Claire']) expect(text).not.toContain(secret);
    expect(text).toContain('[REDACTED]');
  });
});
