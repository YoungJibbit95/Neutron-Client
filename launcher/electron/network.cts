import fs from 'node:fs/promises';
import path from 'node:path';
import { hash } from './files.cjs';
export const hosts = new Set(['api.github.com', 'github.com', 'codeload.github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com', 'api.adoptium.net', 'repo.maven.apache.org', 'piston-meta.mojang.com', 'launchermeta.mojang.com', 'libraries.minecraft.net', 'resources.download.minecraft.net']);
export function checkUrl(value: string) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.port || url.username || url.password || !hosts.has(url.hostname)) throw new Error('Unapproved download host.');
  return url.href;
}
export async function response(url: string, signal: AbortSignal, redirects = 0): Promise<Response> {
  if (redirects > 5) throw new Error('Too many download redirects.');
  signal.throwIfAborted();
  const result = await fetch(checkUrl(url), { redirect: 'manual', signal: AbortSignal.any([signal, AbortSignal.timeout(90000)]), headers: { 'User-Agent': 'Neutron-Launcher/0.1.0-playtest', Accept: 'application/vnd.github+json' } });
  if ([301, 302, 303, 307, 308].includes(result.status)) {
    const location = result.headers.get('location'); await result.body?.cancel();
    if (!location) throw new Error('Missing redirect destination.');
    return response(new URL(location, url).href, signal, redirects + 1);
  }
  if (!result.ok) { await result.body?.cancel(); throw new Error('Download HTTP ' + result.status + ' (' + new URL(url).hostname + ')'); }
  return result;
}
export async function json<T>(url: string, signal: AbortSignal): Promise<T> {
  const result = await response(url, signal);
  const reader = result.body!.getReader(); const chunks: Uint8Array[] = []; let size = 0;
  try { for (;;) { const part = await reader.read(); if (part.done) break; size += part.value.length; if (size > 8 * 1024 * 1024) throw new Error('Metadata exceeds size limit.'); chunks.push(part.value); } }
  finally { reader.releaseLock(); }
  return JSON.parse(Buffer.concat(chunks).toString('utf8')) as T;
}
export interface Download { url: string; digest: string; algorithm?: 'sha256' | 'sha512' | 'sha1'; size?: number; maxBytes?: number }
export async function download(item: Download, target: string, signal: AbortSignal, progress: (received: number, total: number | null) => void = () => {}) {
  const algorithm = item.algorithm ?? 'sha256';
  if (!new RegExp('^[a-f0-9]{' + (algorithm === 'sha512' ? 128 : algorithm === 'sha1' ? 40 : 64) + '}$').test(item.digest)) throw new Error('Invalid expected download digest.');
  try { if (await hash(target, algorithm) === item.digest) return target; } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
  await fs.mkdir(path.dirname(target), { recursive: true });
  const partial = target + '.partial';
  // Interrupted objects restart; only complete, hashed cache objects are reused.
  const result = await response(item.url, signal);
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
