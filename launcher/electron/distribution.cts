import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import { Transform } from 'node:stream';
import { atomicJson, files, hash, owned } from './files.cjs';
import { extractZip, zipDirectory } from './archives.cjs';
import { json, response } from './network.cjs';
import { buildEnvironment, runProcess, type ProcessGroup } from './processes.cjs';
import type { Tools } from './toolchain.cjs';
import type { Runtime } from './minecraft.cjs';
import type { BuildRecord, LaunchOptions } from './contracts.cjs';
import { seedLegacyDependencies } from './legacy-dependencies.cjs';
import { adaptSource } from './source-adapter.cjs';
import { mapLimit } from './parallel.cjs';

export const repository = 'YoungJibbit95/Neutron-Client';
const entryPoint = 'me.neutron.launcher.ClientBootstrap';
export interface Distribution { schemaVersion: 1; entryPoint: string; version: string; commit: string; archiveSha256: string; adapterSha256: string; runtime: Runtime; classpath: { path: string; sha256: string }[] }
export async function resolveMain(signal: AbortSignal) {
  const result = await json<{ sha: string }>(`https://api.github.com/repos/${repository}/commits/main`, signal);
  if (!/^[a-f0-9]{40}$/.test(result.sha)) throw new Error('GitHub returned an invalid commit.'); return result.sha;
}
export async function adapterFingerprint(adapter: string) {
  const value = createHash('sha256');
  for (const name of await files(adapter)) value.update(name + ':' + await hash(path.join(adapter, name)) + '\n');
  return value.digest('hex');
}
export async function reusableDistribution(root: string, builds: BuildRecord[], commit: string, adapter: string, runtime: Runtime, signal: AbortSignal) {
  const fingerprint = await adapterFingerprint(adapter);
  for (const build of builds.filter(item => item.commit === commit)) {
    signal.throwIfAborted();
    try {
      const verified = await verifyDistribution(root, build);
      if (verified.manifest.adapterSha256 === fingerprint && verified.manifest.runtime.directory === runtime.directory && verified.manifest.runtime.manifestSha256 === runtime.manifestSha256) return build;
    } catch { signal.throwIfAborted(); /* A corrupt cache is rebuilt, never activated. */ }
  }
  return null;
}
export async function prepareSource(root: string, commit: string, adapter: string, signal: AbortSignal, expectedArchiveSha256?: string) {
  signal.throwIfAborted();
  if (!/^[a-f0-9]{40}$/.test(commit) || expectedArchiveSha256 !== undefined && !/^[a-f0-9]{64}$/.test(expectedArchiveSha256)) throw new Error('Invalid source pin.');
  const work = await owned(root, 'work', commit + '-' + randomUUID()); await fs.mkdir(work, { recursive: true });
  const archive = path.join(work, 'source.zip'); const cached = await owned(root, 'cache', 'source-' + commit + '.zip'); const receipt = cached + '.json'; let reused = false;
  try { const record = JSON.parse(await fs.readFile(receipt, 'utf8')); reused = record.commit === commit && (!expectedArchiveSha256 || record.sha256 === expectedArchiveSha256) && /^[a-f0-9]{64}$/.test(record.sha256) && await hash(cached) === record.sha256; } catch { /* Incomplete cache is never trusted. */ }
  if (reused) await fs.copyFile(cached, archive);
  else {
    const result = await response(`https://codeload.github.com/${repository}/zip/${commit}`, signal); let bytes = 0;
    await pipeline(result.body!, new Transform({ transform(chunk, _encoding, callback) { bytes += chunk.length; callback(bytes > 512 * 1024 ** 2 ? new Error('Source archive too large.') : null, chunk); } }), createWriteStream(archive, { flags: 'wx' }), { signal });
    signal.throwIfAborted();
    if (expectedArchiveSha256 && await hash(archive) !== expectedArchiveSha256) throw new Error('Pinned Alpha source archive integrity failed. No build was started.');
    await fs.copyFile(archive, cached + '.partial'); await fs.rename(cached + '.partial', cached); await atomicJson(receipt, { commit, sha256: await hash(cached) });
  }
  if (expectedArchiveSha256 && await hash(archive) !== expectedArchiveSha256) throw new Error('Pinned Alpha source archive integrity failed.');
  const source = path.join(work, 'source'); const prefix = 'Neutron-Client-' + commit + '/';
  await extractZip(archive, source, signal, name => {
    if (!name.startsWith(prefix)) throw new Error('Unexpected source archive root.');
    const relative = name.slice(prefix.length); return relative === 'pom.xml' || relative.startsWith('src/') ? relative : null;
  });
  const upstream = await adaptSource(source, adapter, signal);
  const adapterHash = createHash('sha256');
  for (const name of await files(adapter)) adapterHash.update(name + ':' + await hash(path.join(adapter, name)) + '\n');
  const snapshot = { commit, archiveSha256: await hash(archive), adapterSha256: adapterHash.digest('hex'), upstream, files: [] as { path: string; sha256: string }[] };
  snapshot.files = await mapLimit(await files(source), 8, async name => ({ path: name, sha256: await hash(path.join(source, name)) }));
  await atomicJson(path.join(work, 'source-snapshot.json'), snapshot); return { work, source, snapshot };
}
export async function buildDistribution(root: string, commit: string, adapter: string, tools: Tools, runtime: Runtime, signal: AbortSignal, phase: (phase: 'download' | 'build' | 'verify') => void, log: (line: string, error: boolean) => void, group?: ProcessGroup, sourceArchiveSha256?: string, preverified = false): Promise<BuildRecord> {
  phase('download'); await seedLegacyDependencies(root, signal); const prepared = await prepareSource(root, commit, adapter, signal, sourceArchiveSha256);
  const temporary = path.join(prepared.work, 'temp'); await fs.mkdir(temporary);
  const settings = path.join(prepared.work, 'settings.xml'); await fs.writeFile(settings, '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>');
  const boots = (await fs.readdir(path.join(tools.mavenHome, 'boot'))).filter(name => /^plexus-classworlds-.*\.jar$/.test(name));
  if (boots.length !== 1) throw new Error('Maven bootstrap is incomplete.');
  phase('build');
  const code = await runProcess(path.join(tools.javaHome, 'bin/java.exe'), ['-Xms64M', '-Xmx1536M', '-Djava.io.tmpdir=' + temporary, '-Dclassworlds.conf=' + path.join(tools.mavenHome, 'bin/m2.conf'), '-Dmaven.home=' + tools.mavenHome, '-Dmaven.multiModuleProjectDirectory=' + prepared.source, '-cp', path.join(tools.mavenHome, 'boot', boots[0]), 'org.codehaus.plexus.classworlds.launcher.Launcher', '-B', '-ntp', '-C', '-s', settings, '-gs', settings, '-Dmaven.repo.local=' + await owned(root, 'maven-repository'), '-Plauncher-dist', ...(preverified ? ['-DskipTests'] : []), 'clean', 'package'], { cwd: prepared.source, env: buildEnvironment(tools.javaHome, temporary, await owned(root, 'maven-user')), signal, group, line: log });
  if (code !== 0) throw new Error('Maven build failed (exit ' + code + '). See Build logs.');
  phase('verify'); const id = commit + '-' + randomUUID(); const stage = await owned(root, 'candidates', id); await fs.mkdir(stage, { recursive: true });
  const unpacked = path.join(prepared.work, 'jar'); await extractZip(path.join(prepared.source, 'target/neutron-client.jar'), unpacked, signal);
  await fs.access(path.join(unpacked, 'me/neutron/launcher/ClientBootstrap.class'));
  try { await fs.access(path.join(unpacked, 'Start.class')); throw new Error('Test entry point found in client package.'); } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
  await zipDirectory(unpacked, path.join(stage, 'neutron-client.jar'), { 'assets/minecraft/Tenacity/shaders/vertex.vsh': 'assets/minecraft/Tenacity/Shaders/vertex.vsh' });
  const libraries = path.join(prepared.source, 'target/launcher-libraries');
  for (const relative of await files(libraries)) {
    if (!relative.endsWith('.jar') || /^(?:org\/lwjgl\/lwjgl\/|org\/lwjgl\/util\/)/.test(relative)) continue;
    if (/(?:^|\/)junit(?:\/|-)/.test(relative)) throw new Error('Test dependency found in client classpath.');
    const target = await owned(stage, 'libraries', relative); await fs.mkdir(path.dirname(target), { recursive: true }); await fs.copyFile(path.join(libraries, relative), target);
    const header = await fs.open(target, 'r'); const signature = Buffer.alloc(4); try { await header.read(signature, 0, 4, 0); } finally { await header.close(); }
    if (signature.readUInt32LE(0) !== 0x04034b50) throw new Error('Non-JAR content rejected: ' + relative);
  }
  const classpath = []; let bytes = 0;
  for (const name of await files(stage)) { bytes += (await fs.stat(path.join(stage, name))).size; classpath.push({ path: name, sha256: await hash(path.join(stage, name)) }); }
  const manifest: Distribution = { schemaVersion: 1, entryPoint, version: 'Development-' + commit.slice(0, 7), commit, archiveSha256: prepared.snapshot.archiveSha256, adapterSha256: prepared.snapshot.adapterSha256, runtime, classpath };
  signal.throwIfAborted(); await atomicJson(path.join(stage, 'launch-manifest.json'), manifest);
  const record = { id, commit, date: new Date().toISOString(), sizeMiB: Math.ceil(bytes / 1024 ** 2), manifestSha256: await hash(path.join(stage, 'launch-manifest.json')) };
  const verified = await verifyDistribution(root, record, 'candidates'); signal.throwIfAborted();
  // Verify the actual production package before activation. No game window,
  // personal profile, test classes or developer classpath is involved.
  const verifiedRuntime = await import('./minecraft.cjs').then(module => module.verifyRuntime(root, runtime));
  const codepoint = await runProcess(path.join(tools.javaHome, 'bin/java.exe'), [
    '-Xms32M', '-Xmx256M', '-Djava.io.tmpdir=' + temporary,
    '-Djava.library.path=' + path.join(verifiedRuntime.directory, 'natives'), '-cp',
    [...verifiedRuntime.manifest.javaLibraries.map(name => path.join(verifiedRuntime.directory, name)), ...verified.classpath].join(path.delimiter),
    entryPoint, '--gameDir', temporary, '--assetsDir', path.join(verifiedRuntime.directory, 'assets'),
    '--nativesDir', path.join(verifiedRuntime.directory, 'natives'), '--assetIndex', '1.8',
    '--version', manifest.version, '--username', 'NeutronPlayer', '--validateOnly',
  ], { cwd: temporary, env: buildEnvironment(tools.javaHome, temporary, await owned(root, 'maven-user')), signal, group, line: log });
  if (codepoint !== 0) throw new Error('Production client Bootstrap validation failed (exit ' + codepoint + '). Previous version retained.');
  await verifyDistribution(root, record, 'candidates'); signal.throwIfAborted();
  await fs.mkdir(await owned(root, 'versions'), { recursive: true }); await fs.rename(stage, await owned(root, 'versions', id)); return record;
}
export async function verifyDistribution(root: string, build: BuildRecord, folder = 'versions') {
  const directory = await owned(root, folder, build.id); const file = path.join(directory, 'launch-manifest.json');
  if (await hash(file) !== build.manifestSha256) throw new Error('Client manifest integrity failed. Rebuild this version.');
  const manifest = JSON.parse(await fs.readFile(file, 'utf8')) as Distribution;
  if (manifest.schemaVersion !== 1 || manifest.entryPoint !== entryPoint || manifest.commit !== build.commit || !manifest.classpath.length) throw new Error('Invalid client distribution.');
  const classpath = await mapLimit(manifest.classpath, 8, async item => { const target = await owned(directory, item.path); if (await hash(target) !== item.sha256) throw new Error('Client file missing or corrupt: ' + item.path); return target; });
  return { directory, manifest, classpath };
}
export function launchArguments(options: LaunchOptions, maxRam: number, classpath: string[], runtime: string, version: string, temporary: string) {
  if (!Number.isInteger(options.ramMiB) || options.ramMiB < 1024 || options.ramMiB > maxRam || !path.isAbsolute(options.gameDir) || options.width < 640 || options.height < 480 || options.width > 7680 || options.height > 4320) throw new Error('Correct the launch options before starting.');
  // Intentionally limited extra JVM options: never accept agents, classpath,
  // entry points, shell syntax or alternate RAM settings from a text field.
  const extra = options.jvmArgs.trim() ? options.jvmArgs.trim().split(/\s+/) : [];
  if (extra.some(arg => !/^-XX:(?:\+|-)(?:UseG1GC|UseConcMarkSweepGC|UseSerialGC|UseParallelGC|DisableExplicitGC|AlwaysPreTouch)$/.test(arg))) throw new Error('Unsupported extra JVM option. Use the RAM control and supported GC flags.');
  return ['-Xms256M', '-Xmx' + options.ramMiB + 'M', '-Djava.io.tmpdir=' + temporary, '-Djava.library.path=' + path.join(runtime, 'natives'), ...extra, '-cp', classpath.join(path.delimiter), entryPoint, '--gameDir', options.gameDir, '--assetsDir', path.join(runtime, 'assets'), '--nativesDir', path.join(runtime, 'natives'), '--assetIndex', '1.8', '--version', version, '--username', 'NeutronPlayer', '--width', String(options.width), '--height', String(options.height), ...(options.fullscreen ? ['--fullscreen'] : [])];
}
