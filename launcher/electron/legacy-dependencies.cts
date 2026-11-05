import fs from 'node:fs/promises';
import path from 'node:path';
import { download } from './network.cjs';
import { atomicJson, hash, owned } from './files.cjs';

// These artifacts have authoritative Mojang digests but no usable Maven POMs.
// Seed minimal local descriptors rather than accepting unchecksummed fallbacks.
export async function seedLegacyDependencies(root: string, signal: AbortSignal) {
  const meta = await download({ url: 'https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json', digest: 'd546f1707a3f2b7d034eece5ea2e311eda875787', algorithm: 'sha1', maxBytes: 1024 * 1024 }, await owned(root, 'cache/minecraft-1.8.9.json'), signal);
  const metadata = JSON.parse(await fs.readFile(meta, 'utf8')) as { libraries: { name: string; downloads: { artifact?: { path: string; url: string; sha1: string; size: number } } }[] };
  const records = [];
  for (const library of metadata.libraries) {
    if (!/^(?:com\.ibm\.icu:icu4j-core-mojang:|com\.paulscode:|org\.lwjgl\.lwjgl:(?:lwjgl|lwjgl_util):2\.9\.4-nightly-20150209$)/.test(library.name)) continue;
    const artifact = library.downloads.artifact; if (!artifact) throw new Error('Missing official legacy artifact.');
    const jar = await download({ url: artifact.url, digest: artifact.sha1, algorithm: 'sha1', size: artifact.size }, await owned(root, 'cache/objects', artifact.sha1), signal);
    const target = await owned(root, 'maven-repository', artifact.path); await fs.mkdir(path.dirname(target), { recursive: true }); await fs.copyFile(jar, target);
    const [groupId, artifactId, version] = library.name.split(':');
    const pom = target.slice(0, -4) + '.pom';
    await fs.writeFile(pom, `<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>${groupId}</groupId><artifactId>${artifactId}</artifactId><version>${version}</version></project>\n`);
    records.push({ coordinate: library.name, upstreamSha1: artifact.sha1, jarSha256: await hash(target), generatedPomSha256: await hash(pom), sourceUrl: artifact.url });
  }
  if (records.length !== 8) throw new Error('Unexpected legacy dependency set.');
  await atomicJson(await owned(root, 'maven-repository/launcher-official-artifacts.json'), records); return records;
}
