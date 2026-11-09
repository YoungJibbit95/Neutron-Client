import fs from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import yauzl from 'yauzl';
import { atomicJson, hash, owned } from './files.cjs';
import { download } from './network.cjs';
import type { Runtime, RuntimeManifest } from './minecraft.cjs';
import type { BuildRecord, ClientRelease, LaunchOptions } from './contracts.cjs';
import { repository, resolveReleaseCommit, validateClientRelease } from './release-catalog.cjs';
import { mapLimit } from './parallel.cjs';

export { repository } from './release-catalog.cjs';
const entryPoint = 'me.neutron.launcher.ClientBootstrap';
interface BaseDistribution { entryPoint: string; version: string; commit: string; runtime: Runtime; classpath: { path: string; sha256: string }[] }
export interface LegacyDistribution extends BaseDistribution { schemaVersion: 1; archiveSha256: string; adapterSha256: string }
export interface ReleaseDistribution extends BaseDistribution { schemaVersion: 2; release: ClientRelease; versionJson: { path: string; sha256: string } }
export type Distribution = LegacyDistribution | ReleaseDistribution;
const sha256 = (value: unknown): value is string => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
const object = (value: unknown): Record<string, unknown> | null => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null;
const windowsNatives: Record<string, { sha1: string; size: number; classifier: string }> = {
  'org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209': { sha1: 'b84d5102b9dbfabfeb5e43c7e2828d98a7fc80e0', size: 613748, classifier: 'natives-windows' },
  'net.java.jinput:jinput-platform:2.0.5': { sha1: '385ee093e01f587f30ee1c8a2ee7d408fd732e16', size: 155179, classifier: 'natives-windows' },
  'tv.twitch:twitch-platform:6.5': { sha1: '9fdd0fd5aed0817063dcf95b69349a171f447ebd', size: 580098, classifier: 'natives-windows-64' },
  'tv.twitch:twitch-external-platform:4.5': { sha1: 'c3cde57891b935d41b6680a9c5e1502eeab76d86', size: 7457619, classifier: 'natives-windows-64' },
};

/** The release JSON must describe the same Java 8 / 1.8.9 runtime we install. */
export function validateVersionJson(value: unknown, release: ClientRelease) {
  const meta = object(value); const java = object(meta?.javaVersion); const index = object(meta?.assetIndex);
  if (!meta || meta.id !== 'Neutron-' + release.version || meta.mainClass !== 'net.minecraft.client.main.Main' || meta.assets !== '1.8' || java?.majorVersion !== 8 || index?.id !== '1.8' || index.sha1 !== 'f6ad102bcaa53b1a58358f16e376d548d44933ec' || index.size !== 78494 || typeof index.url !== 'string' || !/^https:\/\/(?:launchermeta|piston-meta)\.mojang\.com\/v1\/packages\/f6ad102bcaa53b1a58358f16e376d548d44933ec\/1\.8\.json$/.test(index.url) || !Array.isArray(meta.libraries) || meta.libraries.length > 64 || ['inheritsFrom', 'jar', 'downloads', 'arguments', 'logging'].some(key => key in meta)) throw new Error('Unsupported Neutron version JSON. Expected the standalone Minecraft 1.8.9 / Java 8 release.');
  const found = new Set<string>();
  for (const raw of meta.libraries) {
    const library = object(raw); const natives = object(library?.natives); const downloads = object(library?.downloads);
    if (!library || typeof library.name !== 'string' || !natives || !downloads) throw new Error('Release JSON may contain native libraries only. Java dependencies must be inside the release JAR.');
    let allowed = library.rules === undefined;
    if (library.rules !== undefined) {
      if (!Array.isArray(library.rules)) throw new Error('Unsupported release library rules.');
      for (const rawRule of library.rules) {
        const rule = object(rawRule); const os = rule?.os === undefined ? null : object(rule.os);
        if (!rule || !['allow', 'disallow'].includes(String(rule.action)) || Object.keys(rule).some(key => !['action', 'os'].includes(key)) || rule.os !== undefined && (!os || Object.keys(os).some(key => key !== 'name') || !['windows', 'linux', 'osx'].includes(String(os.name)))) throw new Error('Unsupported release library rule.');
        if (!os || os.name === 'windows') allowed = rule.action === 'allow';
      }
    }
    if (!allowed) continue;
    const expected = windowsNatives[library.name];
    const classifier = typeof natives.windows === 'string' ? natives.windows.replace('${arch}', '64') : '';
    const artifact = object(object(downloads.classifiers)?.[classifier]);
    const parts = library.name.split(':'); const relative = `${parts[0].replace(/\./g, '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}-${classifier}.jar`;
    if (!expected || found.has(library.name) || classifier !== expected.classifier || !artifact || artifact.sha1 !== expected.sha1 || artifact.size !== expected.size || artifact.path !== relative || artifact.url !== 'https://libraries.minecraft.net/' + relative) throw new Error('Release native libraries do not match the supported Windows x64 runtime.');
    found.add(library.name);
  }
  if (found.size !== Object.keys(windowsNatives).length) throw new Error('Release JSON is missing required Windows x64 native libraries.');
  return { id: meta.id as string, mainClass: meta.mainClass as string, assets: '1.8' as const };
}

const requiredClasses = ['me/neutron/launcher/ClientBootstrap.class', 'net/minecraft/client/main/Main.class', 'me/youngjibbit/Pulsar.class', 'org/lwjgl/opengl/Display.class', 'com/sun/jna/Native.class', 'oshi/SystemInfo.class', 'org/apache/logging/log4j/core/LoggerContext.class', 'com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class', 'org/reflections/Reflections.class'];
export async function verifyReleaseJar(file: string, signal: AbortSignal) {
  signal.throwIfAborted();
  await new Promise<void>((resolve, reject) => {
    yauzl.open(file, { lazyEntries: true, validateEntrySizes: true }, (failure, zip) => {
      if (failure || !zip) { reject(new Error('Release JAR is not a valid Java archive.')); return; }
      const found = new Set<string>(); let count = 0; let manifest = false; let finished = false;
      const fail = (error: unknown) => { if (finished) return; finished = true; signal.removeEventListener('abort', abort); zip.close(); reject(error); };
      const abort = () => fail(signal.reason ?? new Error('Cancelled.'));
      signal.addEventListener('abort', abort, { once: true }); zip.on('error', fail);
      zip.on('end', () => { if (finished) return; if (!manifest || requiredClasses.some(name => !found.has(name))) { fail(new Error('Release JAR is missing its production entry point or bundled Java dependencies.')); return; } finished = true; signal.removeEventListener('abort', abort); resolve(); });
      zip.on('entry', (entry: yauzl.Entry) => {
        if (signal.aborted) { abort(); return; }
        if (++count > 100000 || entry.fileName === 'Start.class' || /^META-INF\/.*\.(?:SF|RSA|DSA)$/i.test(entry.fileName)) { fail(new Error('Unsupported test entry point, signatures or oversized release JAR.')); return; }
        if (requiredClasses.includes(entry.fileName)) { if (found.has(entry.fileName)) { fail(new Error('Duplicate production class in release JAR.')); return; } found.add(entry.fileName); }
        if (entry.fileName !== 'META-INF/MANIFEST.MF') { zip.readEntry(); return; }
        if (manifest || entry.uncompressedSize > 65536) { fail(new Error('Invalid release JAR manifest.')); return; }
        zip.openReadStream(entry, (error, stream) => {
          if (error || !stream) { fail(error ?? new Error('Missing release JAR manifest.')); return; }
          const chunks: Buffer[] = []; let bytes = 0;
          stream.on('data', (chunk: Buffer) => { bytes += chunk.length; if (bytes > 65536) { stream.destroy(); fail(new Error('Release JAR manifest is too large.')); } else chunks.push(chunk); });
          stream.on('error', fail); stream.on('end', () => { if (finished) return; if (!/^Main-Class: net\.minecraft\.client\.main\.Main\r?$/m.test(Buffer.concat(chunks).toString('utf8'))) { fail(new Error('Release JAR entry point differs from the version JSON.')); return; } manifest = true; zip.readEntry(); });
        });
      });
      if (signal.aborted) abort(); else zip.readEntry();
    });
  });
}

export async function installReleaseDistribution(root: string, input: ClientRelease, runtime: Runtime, signal: AbortSignal, progress: (received: number, total: number | null) => void, phase: (phase: 'download' | 'verify') => void): Promise<BuildRecord> {
  signal.throwIfAborted(); const release = validateClientRelease(input);
  phase('download');
  const fetchAsset = async (item: ClientRelease['jar']) => download({ url: item.url, digest: item.sha256, size: item.size, fallbackUrls: [`https://api.github.com/repos/${repository}/releases/assets/${item.id}`] }, await owned(root, 'cache/release-assets', item.sha256 + path.extname(item.name)), signal, progress);
  const metadata = await fetchAsset(release.json);
  validateVersionJson(JSON.parse(await fs.readFile(metadata, 'utf8')), release);
  const jar = await fetchAsset(release.jar); const commit = await resolveReleaseCommit(release, signal);
  phase('verify'); await verifyReleaseJar(jar, signal);
  const id = commit + '-' + randomUUID(); const stage = await owned(root, 'candidates', id); await fs.mkdir(stage, { recursive: true });
  try {
    await fs.copyFile(jar, path.join(stage, 'neutron-client.jar'));
    await fs.copyFile(metadata, path.join(stage, 'neutron-version.json'));
    const manifest: ReleaseDistribution = { schemaVersion: 2, entryPoint, version: 'Neutron-' + release.version, commit, runtime, release,
      classpath: [{ path: 'neutron-client.jar', sha256: release.jar.sha256 }], versionJson: { path: 'neutron-version.json', sha256: release.json.sha256 } };
    signal.throwIfAborted(); await atomicJson(path.join(stage, 'launch-manifest.json'), manifest);
    const record: BuildRecord = { id, commit, date: new Date().toISOString(), sizeMiB: Math.ceil((release.jar.size + release.json.size) / 1024 ** 2), manifestSha256: await hash(path.join(stage, 'launch-manifest.json')), releaseTag: release.tag, releaseId: release.id, version: release.version };
    await verifyDistribution(root, record, 'candidates'); signal.throwIfAborted();
    await fs.mkdir(await owned(root, 'versions'), { recursive: true }); await fs.rename(stage, await owned(root, 'versions', id));
    return record;
  } catch (error) { await fs.rm(stage, { recursive: true, force: true }); throw error; }
}

export async function reusableReleaseDistribution(root: string, builds: BuildRecord[], input: ClientRelease, runtime: Runtime, signal: AbortSignal) {
  signal.throwIfAborted(); const release = validateClientRelease(input);
  for (const build of builds.filter(item => item.releaseId === release.id && item.releaseTag === release.tag)) {
    signal.throwIfAborted();
    try {
      const verified = await verifyDistribution(root, build); signal.throwIfAborted(); const manifest = verified.manifest;
      if (manifest.schemaVersion === 2 && manifest.release.jar.id === release.jar.id && manifest.release.json.id === release.json.id && manifest.release.jar.sha256 === release.jar.sha256 && manifest.release.json.sha256 === release.json.sha256 && manifest.runtime.directory === runtime.directory && manifest.runtime.manifestSha256 === runtime.manifestSha256) return build;
    } catch { signal.throwIfAborted(); /* A corrupt or replaced release never becomes active. */ }
  }
  return null;
}

export async function verifyDistribution(root: string, build: BuildRecord, folder = 'versions') {
  if (!/^[a-f0-9]{40}$/.test(build.commit) || !new RegExp('^' + build.commit + '-[a-f0-9-]{36}$').test(build.id) || !sha256(build.manifestSha256)) throw new Error('Invalid installed version record.');
  const directory = await owned(root, folder, build.id); const file = path.join(directory, 'launch-manifest.json');
  if (await hash(file) !== build.manifestSha256) throw new Error('Client manifest integrity failed. Install this release again.');
  const manifest = JSON.parse(await fs.readFile(file, 'utf8')) as Distribution;
  if (![1, 2].includes(manifest.schemaVersion) || manifest.entryPoint !== entryPoint || manifest.commit !== build.commit || typeof manifest.version !== 'string' || !manifest.version || !manifest.runtime || !/^runtimes\/[a-f0-9-]{36}$/.test(manifest.runtime.directory) || !sha256(manifest.runtime.manifestSha256) || !Array.isArray(manifest.classpath) || !manifest.classpath.length || manifest.classpath.length > 4096 || manifest.classpath.some(item => !item || typeof item.path !== 'string' || !item.path.endsWith('.jar') || !sha256(item.sha256)) || new Set(manifest.classpath.map(item => item.path.toLowerCase())).size !== manifest.classpath.length) throw new Error('Invalid client distribution.');
  const classpath = await mapLimit(manifest.classpath, 8, async item => { const target = await owned(directory, item.path); if (await hash(target) !== item.sha256) throw new Error('Client file missing or corrupt: ' + item.path); return target; });
  if (manifest.schemaVersion === 2) {
    const release = validateClientRelease(manifest.release);
    if (manifest.version !== 'Neutron-' + release.version || build.releaseId !== release.id || build.releaseTag !== release.tag || build.version !== release.version || manifest.classpath.length !== 1 || manifest.classpath[0].path !== 'neutron-client.jar' || manifest.classpath[0].sha256 !== release.jar.sha256 || manifest.versionJson?.path !== 'neutron-version.json' || manifest.versionJson.sha256 !== release.json.sha256) throw new Error('Installed client and release metadata do not match.');
    const metadata = await owned(directory, manifest.versionJson.path);
    if ((await fs.stat(classpath[0])).size !== release.jar.size || (await fs.stat(metadata)).size !== release.json.size || await hash(metadata) !== release.json.sha256) throw new Error('Release JAR or version JSON is missing or corrupt.');
    validateVersionJson(JSON.parse(await fs.readFile(metadata, 'utf8')), release);
  }
  return { directory, manifest, classpath };
}

export function clientClasspath(client: { manifest: Distribution; classpath: string[] }, runtime: { directory: string; manifest: RuntimeManifest }) {
  // Release JARs already bundle Java dependencies, including patched LWJGL.
  return client.manifest.schemaVersion === 2 ? [...client.classpath] : [...runtime.manifest.javaLibraries.map(name => path.join(runtime.directory, name)), ...client.classpath];
}
export function launchArguments(options: LaunchOptions, maxRam: number, classpath: string[], runtime: string, version: string, temporary: string) {
  if (!Number.isInteger(options.ramMiB) || options.ramMiB < 1024 || options.ramMiB > maxRam || !path.isAbsolute(options.gameDir) || options.width < 640 || options.height < 480 || options.width > 7680 || options.height > 4320) throw new Error('Correct the launch options before starting.');
  // Intentionally limited extra JVM options: never accept agents, classpath,
  // entry points, shell syntax or alternate RAM settings from a text field.
  const extra = options.jvmArgs.trim() ? options.jvmArgs.trim().split(/\s+/) : [];
  if (extra.some(arg => !/^-XX:(?:\+|-)(?:UseG1GC|UseConcMarkSweepGC|UseSerialGC|UseParallelGC|DisableExplicitGC|AlwaysPreTouch)$/.test(arg))) throw new Error('Unsupported extra JVM option. Use the RAM control and supported GC flags.');
  return ['-Xms256M', '-Xmx' + options.ramMiB + 'M', '-Djava.io.tmpdir=' + temporary, '-Djava.library.path=' + path.join(runtime, 'natives'), ...extra, '-cp', classpath.join(path.delimiter), entryPoint, '--gameDir', options.gameDir, '--assetsDir', path.join(runtime, 'assets'), '--nativesDir', path.join(runtime, 'natives'), '--assetIndex', '1.8', '--version', version, '--username', 'NeutronPlayer', '--width', String(options.width), '--height', String(options.height), ...(options.fullscreen ? ['--fullscreen'] : [])];
}
