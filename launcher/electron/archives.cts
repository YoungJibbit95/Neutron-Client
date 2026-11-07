import fs from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';
import yauzl from 'yauzl';
import yazl from 'yazl';
import { files, owned } from './files.cjs';

export function archivePath(name: string) {
  if (!name || name.includes('\\') || /[:\0]/.test(name) || name.startsWith('/') || name.split('/').some(part => part === '..' || part === '.' || /[. ]$/.test(part) || /^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(part))) throw new Error('Unsafe archive entry: ' + name);
  return name;
}
export async function extractZip(archive: string, destination: string, signal: AbortSignal, select: (name: string) => string | null = name => name) {
  await fs.mkdir(destination, { recursive: true });
  await owned(destination);
  return new Promise<void>((resolve, reject) => {
    yauzl.open(archive, { lazyEntries: true, validateEntrySizes: true }, (error, zip) => {
      if (error || !zip) { reject(error ?? new Error('Cannot read archive.')); return; }
      const seen = new Set<string>(); let bytes = 0; let count = 0;
      const stop = (failure: unknown) => { zip.close(); reject(failure); };
      const abort = () => stop(signal.reason ?? new Error('Cancelled.'));
      signal.addEventListener('abort', abort, { once: true });
      zip.on('error', stop);
      zip.on('end', () => { signal.removeEventListener('abort', abort); resolve(); });
      zip.on('entry', (entry: yauzl.Entry) => { void (async () => {
        signal.throwIfAborted(); archivePath(entry.fileName);
        if (++count > 50000 || (bytes += entry.uncompressedSize) > 2 * 1024 ** 3 || entry.uncompressedSize > 256 * 1024 ** 2) throw new Error('Archive expansion limit exceeded.');
        if ((entry.externalFileAttributes >>> 16 & 0xf000) === 0xa000 || entry.generalPurposeBitFlag & 1) throw new Error('Archive links/encryption are not supported.');
        const relative = select(entry.fileName);
        if (relative === null || relative.endsWith('/')) { zip.readEntry(); return; }
        archivePath(relative);
        const key = relative.toLowerCase(); if (seen.has(key)) throw new Error('Duplicate archive target: ' + relative); seen.add(key);
        const target = await owned(destination, relative); await fs.mkdir(path.dirname(target), { recursive: true });
        const stream = await new Promise<NodeJS.ReadableStream>((yes, no) => zip.openReadStream(entry, (failure, input) => failure || !input ? no(failure) : yes(input)));
        // New extraction trees only; never overwrite another entry or an existing file.
        await pipeline(stream, createWriteStream(target, { flags: 'wx' }), { signal }); zip.readEntry();
      })().catch(stop); });
      if (signal.aborted) abort(); else zip.readEntry();
    });
  });
}
export async function zipDirectory(root: string, target: string, aliases: Record<string, string> = {}) {
  const zip = new yazl.ZipFile();
  for (const relative of await files(root)) zip.addFile(path.join(root, relative), relative);
  for (const [alias, original] of Object.entries(aliases)) { archivePath(alias); zip.addFile(path.join(root, original), alias); }
  const done = pipeline(zip.outputStream, createWriteStream(target, { flags: 'wx' })); zip.end(); await done;
}
