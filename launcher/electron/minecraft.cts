import { mapLimit } from './parallel.cjs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { extractZip } from './archives.cjs';
import { atomicJson, files, hash, owned } from './files.cjs';
import { download } from './network.cjs';

const metadataSha1 = 'd546f1707a3f2b7d034eece5ea2e311eda875787';
interface Artifact { url: string; sha1: string; size: number; path?: string }
interface Library { name: string; rules?: { action: string; os?: { name?: string }; features?: unknown }[]; natives?: Record<string, string>; downloads: { artifact?: Artifact; classifiers?: Record<string, Artifact> } }
interface Metadata { assetIndex: Artifact & { id: string }; libraries: Library[] }
export interface Runtime { directory: string; manifestSha256: string }
export interface RuntimeManifest { schemaVersion: 1; platform: 'windows-x64'; files: { path: string; sha256: string }[]; javaLibraries: string[]; assets: { hash: string; size: number }[] }
export function applies(library: Library) {
  if (!library.rules) return true;
  let allowed = false;
  for (const rule of library.rules) {
    if (rule.features || Object.keys(rule.os ?? {}).some(key => key !== 'name') || !['allow', 'disallow'].includes(rule.action)) throw new Error('Unsupported Minecraft library rule.');
    if (!rule.os?.name || rule.os.name === 'windows') allowed = rule.action === 'allow';
  }
  return allowed;
}
export async function installRuntime(root: string, signal: AbortSignal, progress: (received: number, total: number | null) => void): Promise<Runtime> {
  const directory = 'runtimes/' + randomUUID(); const stage = await owned(root, directory); await fs.mkdir(stage, { recursive: true });
  const metaFile = await download({ url: `https://piston-meta.mojang.com/v1/packages/${metadataSha1}/1.8.9.json`, digest: metadataSha1, algorithm: 'sha1', maxBytes: 1024 * 1024 }, await owned(root, 'cache/minecraft-1.8.9.json'), signal);
  const meta = JSON.parse(await fs.readFile(metaFile, 'utf8')) as Metadata;
  if (meta.assetIndex.id !== '1.8' || !Array.isArray(meta.libraries)) throw new Error('Unexpected Minecraft metadata.');
  const fetchArtifact = async (item: Artifact, name: string) => {
    if (!/^[a-f0-9]{40}$/.test(item.sha1) || !Number.isSafeInteger(item.size) || item.size < 0 || item.size > 256 * 1024 ** 2) throw new Error('Invalid Minecraft artifact.');
    const cache = await download({ url: item.url, digest: item.sha1, algorithm: 'sha1', size: item.size }, await owned(root, 'cache/objects', item.sha1), signal, progress);
    const target = await owned(stage, name); await fs.mkdir(path.dirname(target), { recursive: true }); await fs.copyFile(cache, target); return target;
  };
  const indexFile = await fetchArtifact(meta.assetIndex, 'assets/indexes/1.8.json');
  const index = JSON.parse(await fs.readFile(indexFile, 'utf8')) as { objects: Record<string, { hash: string; size: number }> };
  const unique = [...new Map(Object.values(index.objects).map(item => [item.hash, item])).values()];
  if (unique.length > 10000 || unique.reduce((sum, item) => sum + item.size, 0) > 512 * 1024 ** 2) throw new Error('Asset index exceeds supported size.');
  await mapLimit(unique, 12, async item => {
    signal.throwIfAborted();
    await fetchArtifact({ url: `https://resources.download.minecraft.net/${item.hash.slice(0, 2)}/${item.hash}`, sha1: item.hash, size: item.size }, `assets/objects/${item.hash.slice(0, 2)}/${item.hash}`);
  });
  await fs.mkdir(path.join(stage, 'natives')); const javaLibraries: string[] = [];
  for (const [number, library] of meta.libraries.entries()) {
    signal.throwIfAborted(); if (!applies(library)) continue;
    const native = library.natives?.windows;
    if (native) {
      const classifier = native.replace('${arch}', '64'); const artifact = library.downloads.classifiers?.[classifier];
      if (!artifact) throw new Error('Missing native classifier.');
      const archive = await fetchArtifact(artifact, `native-archives/${number}.jar`);
      const extracted = path.join(stage, 'native-extract', String(number));
      await extractZip(archive, extracted, signal, name => !name.includes('/') && name.endsWith('.dll') ? name : null);
      for (const name of await files(extracted)) {
        const target = path.join(stage, 'natives', name); const source = path.join(extracted, name);
        try { if (await hash(target) !== await hash(source)) throw new Error('Conflicting native files.'); } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; await fs.copyFile(source, target); }
      }
    }
    if (/^org\.lwjgl\.lwjgl:(?:lwjgl|lwjgl_util):/.test(library.name)) {
      const artifact = library.downloads.artifact; if (!artifact?.path) throw new Error('Missing Java binding.');
      const name = 'java-libraries/' + path.posix.basename(artifact.path); await fetchArtifact(artifact, name); javaLibraries.push(name);
    }
  }
  if (javaLibraries.length !== 2) throw new Error('Expected a matched LWJGL binding pair.');
  for (const name of ['lwjgl64.dll', 'OpenAL64.dll', 'jinput-raw_64.dll']) {
    const binary = await fs.readFile(path.join(stage, 'natives', name)); const offset = binary.readUInt32LE(0x3c);
    if (binary.toString('ascii', 0, 2) !== 'MZ' || offset + 6 > binary.length || binary.readUInt32LE(offset) !== 0x4550 || binary.readUInt16LE(offset + 4) !== 0x8664) throw new Error('Windows x64 native header check failed.');
  }
  const records = [];
  for (const name of await files(stage)) if (name.startsWith('java-libraries/') || name.startsWith('natives/') || name === 'assets/indexes/1.8.json') records.push({ path: name, sha256: await hash(path.join(stage, name)) });
  const manifest: RuntimeManifest = { schemaVersion: 1, platform: 'windows-x64', files: records, javaLibraries, assets: unique };
  signal.throwIfAborted(); await atomicJson(path.join(stage, 'runtime-manifest.json'), manifest);
  return { directory, manifestSha256: await hash(path.join(stage, 'runtime-manifest.json')) };
}
export async function verifyRuntime(root: string, runtime: Runtime) {
  const directory = await owned(root, runtime.directory); const file = path.join(directory, 'runtime-manifest.json');
  if (await hash(file) !== runtime.manifestSha256) throw new Error('Runtime manifest integrity failed. Run setup again.');
  const manifest = JSON.parse(await fs.readFile(file, 'utf8')) as RuntimeManifest;
  if (manifest.schemaVersion !== 1 || manifest.platform !== 'windows-x64' || manifest.javaLibraries.length !== 2) throw new Error('Unsupported runtime.');
  await mapLimit(manifest.files, 8, async item => { if (await hash(await owned(directory, item.path)) !== item.sha256) throw new Error('Runtime file is missing or corrupt: ' + item.path); });
  await mapLimit(manifest.assets, 8, async item => {
    if (!/^[a-f0-9]{40}$/.test(item.hash)) throw new Error('Invalid asset hash.');
    const target = await owned(directory, 'assets/objects', item.hash.slice(0, 2), item.hash);
    if ((await fs.stat(target)).size !== item.size || await hash(target, 'sha1') !== item.hash) throw new Error('Minecraft asset missing or corrupt. Run setup again.');
  });
  return { directory, manifest };
}
