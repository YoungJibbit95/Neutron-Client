import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { createReadStream } from 'node:fs';

export async function hash(file: string, algorithm = 'sha256') {
  const value = createHash(algorithm);
  for await (const chunk of createReadStream(file)) value.update(chunk);
  return value.digest('hex');
}
export async function atomicJson(file: string, value: unknown) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  const temporary = file + '.' + randomUUID() + '.tmp';
  const handle = await fs.open(temporary, 'wx');
  try { await handle.writeFile(JSON.stringify(value, null, 2) + '\n'); await handle.sync(); } finally { await handle.close(); }
  await fs.rename(temporary, file);
}
// All writes/removals within owned storage use this guard, including existing ancestors.
export async function owned(root: string, ...segments: string[]) {
  const target = path.resolve(root, ...segments);
  const relative = path.relative(path.resolve(root), target);
  if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Path leaves launcher storage.');
  const chain: string[] = [];
  for (let current = target;; current = path.dirname(current)) { chain.push(current); if (current === path.dirname(current)) break; }
  for (const current of chain.reverse()) {
    try { if ((await fs.lstat(current)).isSymbolicLink()) throw new Error('Linked launcher storage is not allowed: ' + current); }
    catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
  }
  return target;
}
export async function files(root: string, prefix = ''): Promise<string[]> {
  const result: string[] = [];
  for (const entry of await fs.readdir(path.join(root, prefix), { withFileTypes: true })) {
    if (entry.isSymbolicLink()) throw new Error('Unexpected symbolic link.');
    const relative = path.posix.join(prefix, entry.name);
    if (entry.isDirectory()) result.push(...await files(root, relative)); else if (entry.isFile()) result.push(relative);
  }
  return result.sort();
}
export function redact(value: string) {
  return value.replace(/Bearer\s+[\w.\-]+/gi, 'Bearer [REDACTED]')
    .replace(/((?:access[_-]?token|refresh[_-]?token|authorization|password|client[_-]?secret)["']?\s*[:=]\s*["']?)[^\s"',;]+/gi, '$1[REDACTED]')
    .replace(/[A-Z]:\\Users\\[^\\\s]+/gi, 'C:\\Users\\[USER]').replace(/\/(?:Users|home)\/[^/\s]+/g, '/home/[USER]');
}
