import fs from 'node:fs/promises';
import path from 'node:path';
import { mapLimit } from './parallel.cjs';
import { download } from './network.cjs';
import { extractZip } from './archives.cjs';
import { atomicJson, files, hash, owned } from './files.cjs';
import { childEnvironment, runProcess, type ProcessGroup } from './processes.cjs';

export const toolchainId = 'temurin8u482b08-maven3.9.15';
export const javaPackage = { url: 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u482-b08/OpenJDK8U-jdk_x64_windows_hotspot_8u482b08.zip', digest: '51637ae41af1bce379a9d059109820864949c8d57ca302e0d502fcf329d33a72', size: 106448448 };
export const mavenPackage = { url: 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip', algorithm: 'sha512' as const, digest: '251c676eb88684d6dd86c666817979d256a335c31ad34ab0e1df17eeca1cd504a6c890e906a46d04e51cc7a4fb02f33a75971ef750558034e0a81c257be02267' };
export interface Tools { toolchainId: string; javaHome: string; mavenHome: string; files: { path: string; sha256: string }[] }
export async function verifyTools(root: string): Promise<Tools> {
  const tools = JSON.parse(await fs.readFile(await owned(root, 'tools/manifest.json'), 'utf8')) as Tools;
  if (tools.toolchainId !== toolchainId) throw new Error('Managed toolchain update required. Run setup to install Temurin 8u482 and Maven 3.9.15; existing versions and game data are retained.');
  if (!Array.isArray(tools.files) || tools.files.length < 10) throw new Error('Toolchain manifest missing.');
  await mapLimit(tools.files, 8, async record => { if (!/^[a-f0-9]{64}$/.test(record.sha256) || await hash(await owned(root, 'tools', record.path)) !== record.sha256) throw new Error('Managed toolchain integrity check failed. Run setup again.'); });
  return { ...tools, javaHome: await owned(root, 'tools', tools.javaHome), mavenHome: await owned(root, 'tools', tools.mavenHome) };
}
export async function installTools(root: string, signal: AbortSignal, progress: (received: number, total: number | null) => void, group?: ProcessGroup) {
  try { return await verifyTools(root); } catch { /* A new immutable installation is built; old files are retained. */ }
  const id = toolchainId + '-' + Date.now();
  const stage = await owned(root, 'tools', id); await fs.mkdir(stage, { recursive: true });
  const javaZip = await download(javaPackage, await owned(root, 'cache/jdk8u482b08.zip'), signal, progress);
  const mavenZip = await download(mavenPackage, await owned(root, 'cache/maven3.9.15.zip'), signal, progress);
  await extractZip(javaZip, path.join(stage, 'jdk'), signal);
  await extractZip(mavenZip, path.join(stage, 'maven'), signal);
  const jdkRoot = (await fs.readdir(path.join(stage, 'jdk')))[0];
  const mavenRoot = (await fs.readdir(path.join(stage, 'maven')))[0];
  const javaHome = path.posix.join(id, 'jdk', jdkRoot); const mavenHome = path.posix.join(id, 'maven', mavenRoot);
  await validateJava(path.join(root, 'tools', javaHome, 'bin/java.exe'), path.join(root, 'temp'), signal, group);
  await fs.access(path.join(root, 'tools', javaHome, 'bin/javac.exe'));
  const records = await Promise.all((await files(stage)).map(async relative => ({ path: path.posix.join(id, relative), sha256: await hash(path.join(stage, relative)) })));
  signal.throwIfAborted(); await atomicJson(await owned(root, 'tools/manifest.json'), { toolchainId, javaHome, mavenHome, files: records });
  return verifyTools(root);
}
export async function validateJava(java: string, temporary: string, signal?: AbortSignal, group?: ProcessGroup) {
  const javaHome = path.dirname(path.dirname(java)); await fs.mkdir(temporary, { recursive: true }); let output = '';
  const code = await runProcess(java, ['-XshowSettings:properties', '-version'], { cwd: temporary, env: childEnvironment(javaHome, temporary), signal, group, line: line => { output += line + '\n'; } });
  if (code || !/java.specification.version\s*=\s*1\.8\s/.test(output) || !/sun.arch.data.model\s*=\s*64\s/.test(output) || !/os.name\s*=\s*Windows/.test(output)) throw new Error('Select a Windows x64 Java 8 runtime.');
  return javaHome;
}
