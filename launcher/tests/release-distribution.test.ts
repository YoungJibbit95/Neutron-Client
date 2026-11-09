import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { installReleaseDistribution, reusableReleaseDistribution, validateVersionJson, verifyDistribution, clientClasspath } from '../electron/distribution.cts';
import { fixtureCommit, fixtureRuntime, jarFixture, releaseFixture, versionJson } from './release-fixtures';
import type { ClientRelease } from '../electron/contracts.cts';

let root: string;
beforeEach(async () => { root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-release-')); });
afterEach(async () => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); await fs.rm(root, { recursive: true, force: true }); });
async function fixture(options: { metadata?: unknown; omitClass?: string } = {}) {
  const jar = await jarFixture(options.omitClass); const metadata = Buffer.from(JSON.stringify(options.metadata ?? versionJson())); const release = releaseFixture(jar, metadata);
  const fetcher = vi.fn(async (url: string) => {
    if (url === release.jar.url) return new Response(new Uint8Array(jar));
    if (url === release.json.url) return new Response(new Uint8Array(metadata));
    if (url.endsWith('/commits/' + release.tag)) return new Response(JSON.stringify({ sha: fixtureCommit }));
    throw new Error('Unexpected network request: ' + url);
  });
  vi.stubGlobal('fetch', fetcher); return { release, fetcher };
}
const install = (release: ClientRelease, signal = new AbortController().signal) => installReleaseDistribution(root, release, fixtureRuntime, signal, () => {}, () => {});

test('installs the exact JAR and JSON pair without source archive or build process and excludes shadowing Java libraries', async () => {
  const { release, fetcher } = await fixture();
  const record = await install(release); const client = await verifyDistribution(root, record);
  expect(record).toMatchObject({ commit: fixtureCommit, releaseId: 123, releaseTag: 'v1.2.3', version: '1.2.3' });
  expect(client.manifest.schemaVersion).toBe(2);
  expect(fetcher.mock.calls.map(([url]) => url)).toEqual([release.json.url, release.jar.url, 'https://api.github.com/repos/YoungJibbit95/Neutron-Client/commits/v1.2.3']);
  expect(clientClasspath(client, { directory: root, manifest: { schemaVersion: 1, platform: 'windows-x64', files: [], assets: [], javaLibraries: ['must-not-shadow.jar', 'also-not.jar'] } })).toEqual([path.join(client.directory, 'neutron-client.jar')]);
  expect(await fs.readdir(client.directory)).toEqual(['launch-manifest.json', 'neutron-client.jar', 'neutron-version.json']);
});
test.each(['version', 'main', 'java', 'assets', 'native', 'java-library', 'inherits'])('rejects incompatible version metadata: %s', mutation => {
  const value: any = versionJson();
  if (mutation === 'version') value.id = 'Neutron-other';
  if (mutation === 'main') value.mainClass = 'other.Main';
  if (mutation === 'java') value.javaVersion.majorVersion = 21;
  if (mutation === 'assets') value.assetIndex.sha1 = '0'.repeat(40);
  if (mutation === 'native') Object.values(value.libraries[0].downloads.classifiers).forEach((item: any) => { item.sha1 = '0'.repeat(40); });
  if (mutation === 'java-library') value.libraries.push({ name: 'remote:java:1', downloads: {} });
  if (mutation === 'inherits') value.inheritsFrom = '1.8.9';
  expect(() => validateVersionJson(value, releaseFixture())).toThrow();
});
test('malformed release JSON prevents downloading a JAR or activating a version', async () => {
  const { release, fetcher } = await fixture({ metadata: { ...versionJson(), mainClass: 'other.Main' } });
  await fs.writeFile(path.join(root, 'state.json'), 'previous playable registry');
  await expect(install(release)).rejects.toThrow('Unsupported Neutron');
  expect(fetcher.mock.calls.map(([url]) => url)).toEqual([release.json.url]);
  expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe('previous playable registry');
  await expect(fs.access(path.join(root, 'versions'))).rejects.toThrow();
});
test('a checksum failure leaves the active registry and old cache object intact', async () => {
  const { release, fetcher } = await fixture(); const target = path.join(root, 'cache/release-assets', release.jar.sha256 + '.jar');
  await fs.mkdir(path.dirname(target), { recursive: true }); await fs.writeFile(target, 'previous bytes'); await fs.writeFile(path.join(root, 'state.json'), 'previous playable registry');
  const original = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async url => url === release.jar.url ? new Response('bad download') : original(url));
  await expect(install(release)).rejects.toThrow('checksum');
  expect(await fs.readFile(target, 'utf8')).toBe('previous bytes'); expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe('previous playable registry');
});
test('a JAR missing Bootstrap cannot be installed even when its digest matches', async () => {
  const { release } = await fixture({ omitClass: 'me/neutron/launcher/ClientBootstrap.class' });
  await expect(install(release)).rejects.toThrow('production entry point');
  await expect(fs.access(path.join(root, 'versions'))).rejects.toThrow();
});
test('both installed release assets are reverified and tampered JSON is rejected before launch', async () => {
  const { release } = await fixture(); const record = await install(release); const client = await verifyDistribution(root, record);
  await fs.writeFile(path.join(client.directory, 'neutron-version.json'), '{}');
  await expect(verifyDistribution(root, record)).rejects.toThrow('corrupt');
});
test('warm offline reuse requires matching release asset identities, digests and runtime', async () => {
  const { release, fetcher } = await fixture(); const record = await install(release); fetcher.mockClear(); vi.stubEnv('NEUTRON_OFFLINE', '1');
  const signal = new AbortController().signal;
  expect(await reusableReleaseDistribution(root, [record], release, fixtureRuntime, signal)).toEqual(record);
  expect(fetcher).not.toHaveBeenCalled();
  expect(await reusableReleaseDistribution(root, [record], { ...release, jar: { ...release.jar, id: 126 } }, fixtureRuntime, signal)).toBeNull();
  expect(await reusableReleaseDistribution(root, [record], { ...release, jar: { ...release.jar, sha256: 'c'.repeat(64) } }, fixtureRuntime, signal)).toBeNull();
  expect(await reusableReleaseDistribution(root, [record], release, { ...fixtureRuntime, manifestSha256: 'd'.repeat(64) }, signal)).toBeNull();
  await fs.writeFile(path.join(root, 'versions', record.id, 'neutron-client.jar'), 'corrupt');
  expect(await reusableReleaseDistribution(root, [record], release, fixtureRuntime, signal)).toBeNull();
});
test('pre-cancelled installation and reuse do not touch storage or networking', async () => {
  const { release, fetcher } = await fixture(); const controller = new AbortController(); controller.abort(new Error('cancelled fixture'));
  await expect(install(release, controller.signal)).rejects.toThrow('cancelled fixture');
  await expect(reusableReleaseDistribution(root, [], release, fixtureRuntime, controller.signal)).rejects.toThrow('cancelled fixture');
  expect(fetcher).not.toHaveBeenCalled(); expect(await fs.readdir(root)).toEqual([]);
});
