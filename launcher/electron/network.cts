import fs from 'node:fs/promises';
import path from 'node:path';
import { hash } from './files.cjs';
export const hosts = new Set(['api.github.com', 'github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com', 'piston-meta.mojang.com', 'launchermeta.mojang.com', 'libraries.minecraft.net', 'resources.download.minecraft.net']);
export function checkUrl(value: string) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.port || url.username || url.password || !hosts.has(url.hostname)) throw new Error('Unapproved download host.');
  return url.href;
}
export async function response(url: string, signal: AbortSignal, redirects = 0): Promise<Response> {
  if (process.env.NEUTRON_OFFLINE === '1') throw new Error('Networking is disabled for offline operation.');
  if (redirects > 5) throw new Error('Too many download redirects.');
  signal.throwIfAborted();
  const binaryAsset = /^https:\/\/api\.github\.com\/repos\/[^/]+\/[^/]+\/releases\/assets\/[0-9]+$/.test(url);
  const result = await fetch(checkUrl(url), { redirect: 'manual', signal: AbortSignal.any([signal, AbortSignal.timeout(90000)]), headers: { 'User-Agent': 'Neutron-Launcher/0.3.0', Accept: binaryAsset ? 'application/octet-stream' : 'application/vnd.github+json' } });
  if ([301, 302, 303, 307, 308].includes(result.status)) {
    const location = result.headers.get('location'); await result.body?.cancel();
    if (!location) throw new Error('Missing redirect destination.');
    return response(new URL(location, url).href, signal, redirects + 1);
  }
  if (!result.ok) {
    await result.body?.cancel();
    if (new URL(url).hostname === 'api.github.com' && (result.status === 429 || result.status === 403 && result.headers.get('x-ratelimit-remaining') === '0')) throw new Error('GitHub request limit reached. Installed versions remain available; retry the release refresh later.');
    throw new Error('Download HTTP ' + result.status + ' (' + new URL(url).hostname + ')');
  }
  return result;
}
export async function json<T>(url: string, signal: AbortSignal): Promise<T> {
  const result = await response(url, signal);
  const reader = result.body!.getReader(); const chunks: Uint8Array[] = []; let size = 0;
  try { for (;;) { const part = await reader.read(); if (part.done) break; size += part.value.length; if (size > 8 * 1024 * 1024) throw new Error('Metadata exceeds size limit.'); chunks.push(part.value); } }
  finally { reader.releaseLock(); }
  return JSON.parse(Buffer.concat(chunks).toString('utf8')) as T;
}
export interface Download { url: string; digest: string; algorithm?: 'sha256' | 'sha512' | 'sha1'; size?: number; maxBytes?: number; fallbackUrls?: string[] }
export async function download(item: Download, target: string, signal: AbortSignal, progress: (received: number, total: number | null) => void = () => {}) {
  signal.throwIfAborted();
  const algorithm = item.algorithm ?? 'sha256';
  if (!new RegExp('^[a-f0-9]{' + (algorithm === 'sha512' ? 128 : algorithm === 'sha1' ? 40 : 64) + '}$').test(item.digest)) throw new Error('Invalid expected download digest.');
  try { if (await hash(target, algorithm) === item.digest && (item.size === undefined || (await fs.stat(target)).size === item.size)) { signal.throwIfAborted(); return target; } } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
  await fs.mkdir(path.dirname(target), { recursive: true });
  const partial = target + '.partial';
  // Interrupted objects restart; only complete, hashed cache objects are reused.
  let result: Response | undefined;
  try { result = await response(item.url, signal); }
  catch (error) {
    // This is a transport fallback only. Never retry a bad checksum, bad
    // URL, 4xx, interrupted operation or a different error as if it were valid.
    if (!/^Download HTTP (?:502|503|504) \(github\.com\)$/.test(String((error as Error).message))
        || !item.fallbackUrls?.length) throw error;
    signal.throwIfAborted();
    let lastError: unknown = error;
    for (const alternate of item.fallbackUrls) {
      try { result = await response(alternate, signal); lastError = null; break; }
      catch (failure) { lastError = failure; }
    }
    if (lastError) throw lastError;
  }
  if (!result) throw new Error('Download did not provide a response.');
  const total = item.size ?? (Number(result.headers.get('content-length')) || null);
  const max = item.maxBytes ?? item.size ?? 512 * 1024 * 1024;
  const handle = await fs.open(partial, 'w'); let received = 0;
  try {
    for await (const chunk of result.body!) { signal.throwIfAborted(); received += chunk.length; if (received > max) throw new Error('Download exceeds size limit.'); await handle.write(chunk); progress(received, total); }
    await handle.sync();
  } finally { await handle.close(); }
  if (item.size !== undefined && received !== item.size || await hash(partial, algorithm) !== item.digest) throw new Error('Download checksum/size mismatch.');
  signal.throwIfAborted(); await fs.rename(partial, target); return target;
}
