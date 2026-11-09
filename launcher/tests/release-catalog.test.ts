import { afterEach, expect, test, vi } from 'vitest';
import { listClientReleases, parseClientReleases, validateClientRelease } from '../electron/release-catalog.cts';
import { githubRelease, releaseFixture } from './release-fixtures';

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });
test('catalog lists complete client pairs newest first and labels prereleases independently of launcher releases', () => {
  const old = githubRelease(); const newer = githubRelease(releaseFixture(undefined, undefined, '1.3.0-alpha.1')); newer.id = 200; newer.published_at = '2026-11-10T12:00:00Z';
  const input = [old, { ...old, id: 300, tag_name: 'launcher-v9.0.0' }, { ...old, id: 301, draft: true }, { ...old, id: 302, assets: [old.assets[0]] }, newer];
  expect(parseClientReleases(input).map(item => [item.tag, item.prerelease])).toEqual([['v1.3.0-alpha.1', true], ['v1.2.3', false]]);
});
test.each(['url', 'digest', 'name', 'duplicate', 'oversize'])('catalog rejects a mismatched or unverifiable asset: %s', mutation => {
  const raw = githubRelease();
  if (mutation === 'url') raw.assets[0].browser_download_url = raw.assets[0].browser_download_url.replace('YoungJibbit95', 'OtherOwner');
  if (mutation === 'digest') raw.assets[0].digest = '';
  if (mutation === 'name') raw.assets[0].name = 'Neutron-0.0.0.jar';
  if (mutation === 'duplicate') raw.assets.push(raw.assets[0]);
  if (mutation === 'oversize') raw.assets[1].size = 2 * 1024 ** 2;
  expect(parseClientReleases([raw])).toEqual([]);
});
test('untrusted normalized records cannot substitute a URL, tag traversal, or duplicate asset', () => {
  const release = releaseFixture(); expect(validateClientRelease(release)).toEqual(release);
  for (const value of [{ ...release, tag: '../main' }, { ...release, jar: { ...release.jar, url: 'https://github.com/Other/Repo/file.jar' } }, { ...release, json: { ...release.json, id: release.jar.id } }]) expect(() => validateClientRelease(value)).toThrow();
});
test('pagination finds the newest client release behind a full page of launcher-only releases', async () => {
  const first = Array.from({ length: 100 }, (_, i) => ({ ...githubRelease(), id: 1000 + i, tag_name: 'launcher-v0.3.' + i }));
  const fetcher = vi.fn(async (url: string) => new Response(JSON.stringify(url.endsWith('page=1') ? first : [githubRelease()])));
  vi.stubGlobal('fetch', fetcher);
  expect((await listClientReleases(new AbortController().signal)).map(item => item.tag)).toEqual(['v1.2.3']);
  expect(fetcher).toHaveBeenCalledTimes(2); expect(fetcher.mock.calls[1][0]).toContain('page=2');
});
test('rate limits produce an actionable retry error and cancellation never starts a catalog request', async () => {
  const fetcher = vi.fn(async () => new Response('{}', { status: 403, headers: { 'x-ratelimit-remaining': '0' } })); vi.stubGlobal('fetch', fetcher);
  await expect(listClientReleases(new AbortController().signal)).rejects.toThrow('GitHub request limit reached');
  fetcher.mockClear(); const controller = new AbortController(); controller.abort(new Error('stopped'));
  await expect(listClientReleases(controller.signal)).rejects.toThrow('stopped'); expect(fetcher).not.toHaveBeenCalled();
});
