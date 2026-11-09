import { json } from './network.cjs';
import type { ClientRelease, ReleaseAsset } from './contracts.cjs';

export const repository = 'YoungJibbit95/Neutron-Client';
const tagPattern = /^v\d+\.\d+\.\d+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?$/;
const digestPattern = /^[a-f0-9]{64}$/;
const positiveId = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) > 0;
const object = (value: unknown): Record<string, unknown> | null => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null;

function asset(value: unknown, tag: string, name: string, max: number): ReleaseAsset | null {
  const item = object(value);
  if (!item || !positiveId(item.id) || item.name !== name || item.state !== 'uploaded' || !positiveId(item.size) || item.size > max || typeof item.digest !== 'string' || !/^sha256:[a-f0-9]{64}$/.test(item.digest)) return null;
  const url = `https://github.com/${repository}/releases/download/${tag}/${name}`;
  if (item.browser_download_url !== url) return null;
  return { id: item.id, name, url, sha256: item.digest.slice(7), size: item.size };
}

/** Only complete, published client pairs enter the executable release catalog. */
export function parseClientReleases(value: unknown): ClientRelease[] {
  if (!Array.isArray(value) || value.length > 100) throw new Error('GitHub returned an invalid release catalog.');
  const releases: ClientRelease[] = [];
  for (const raw of value) {
    const item = object(raw);
    if (!item || item.draft !== false || !positiveId(item.id) || typeof item.tag_name !== 'string' || !tagPattern.test(item.tag_name) || typeof item.prerelease !== 'boolean' || typeof item.published_at !== 'string' || !Number.isFinite(Date.parse(item.published_at)) || !Array.isArray(item.assets)) continue;
    const version = item.tag_name.slice(1); const stem = 'Neutron-' + version;
    const assets = item.assets;
    const matching = (suffix: string) => assets.filter((rawAsset: unknown) => object(rawAsset)?.name === stem + suffix);
    const jars = matching('.jar'); const metadata = matching('.json');
    if (jars.length !== 1 || metadata.length !== 1) continue;
    const jar = asset(jars[0], item.tag_name, stem + '.jar', 512 * 1024 ** 2);
    const manifest = asset(metadata[0], item.tag_name, stem + '.json', 1024 * 1024);
    if (!jar || !manifest || jar.id === manifest.id) continue;
    releases.push({ id: item.id, tag: item.tag_name, version, publishedAt: item.published_at, prerelease: item.prerelease || version.includes('-'), jar, json: manifest });
  }
  return releases.sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || b.id - a.id);
}

/** Revalidate persisted/packaged catalog records without trusting supplied URLs. */
export function validateClientRelease(value: unknown): ClientRelease {
  const release = object(value);
  if (!release || !positiveId(release.id) || typeof release.tag !== 'string' || !tagPattern.test(release.tag) || release.version !== release.tag.slice(1) || typeof release.publishedAt !== 'string' || !Number.isFinite(Date.parse(release.publishedAt)) || typeof release.prerelease !== 'boolean') throw new Error('Invalid Neutron release record.');
  const parse = (key: 'jar' | 'json', max: number) => {
    const item = object(release[key]); const name = `Neutron-${release.version}.${key}`;
    if (!item || !positiveId(item.id) || item.name !== name || item.url !== `https://github.com/${repository}/releases/download/${release.tag}/${name}` || typeof item.sha256 !== 'string' || !digestPattern.test(item.sha256) || !positiveId(item.size) || item.size > max) throw new Error('Invalid Neutron release asset.');
    return { id: item.id, name, url: item.url, sha256: item.sha256, size: item.size } as ReleaseAsset;
  };
  const jar = parse('jar', 512 * 1024 ** 2); const metadata = parse('json', 1024 * 1024);
  if (jar.id === metadata.id) throw new Error('Release JAR and JSON must be distinct assets.');
  return { id: release.id, tag: release.tag, version: release.version as string, publishedAt: release.publishedAt, prerelease: release.prerelease, jar, json: metadata };
}

export async function listClientReleases(signal: AbortSignal): Promise<ClientRelease[]> {
  const releases = new Map<number, ClientRelease>();
  for (let page = 1; page <= 10; page++) {
    signal.throwIfAborted();
    const raw = await json<unknown>(`https://api.github.com/repos/${repository}/releases?per_page=100&page=${page}`, signal);
    for (const release of parseClientReleases(raw)) releases.set(release.id, release);
    if ((raw as unknown[]).length < 100) return [...releases.values()].sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || b.id - a.id);
  }
  throw new Error('GitHub release catalog exceeds the supported size. Retry after older releases are archived.');
}

export async function resolveReleaseCommit(release: ClientRelease, signal: AbortSignal) {
  const verified = validateClientRelease(release);
  const result = await json<{ sha?: unknown }>(`https://api.github.com/repos/${repository}/commits/${encodeURIComponent(verified.tag)}`, signal);
  if (!result || typeof result.sha !== 'string' || !/^[a-f0-9]{40}$/.test(result.sha)) throw new Error('GitHub returned an invalid release commit.');
  return result.sha;
}
