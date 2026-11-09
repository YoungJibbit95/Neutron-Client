import fs from 'node:fs/promises';
import path from 'node:path';
import { mapLimit } from './parallel.cjs';
import { download } from './network.cjs';
import { extractZip } from './archives.cjs';
import { atomicJson, files, hash, owned } from './files.cjs';
import { childEnvironment, runProcess, type ProcessGroup } from './processes.cjs';

export const toolchainId = 'temurin8u482b08';
const legacyToolchainId = 'temurin8u482b08-maven3.9.15';
export const javaPackage = { url: 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u482-b08/OpenJDK8U-jdk_x64_windows_hotspot_8u482b08.zip', digest: '51637ae41af1bce379a9d059109820864949c8d57ca302e0d502fcf329d33a72', size: 106448448, fallbackUrls: ['https://api.github.com/repos/adoptium/temurin8-binaries/releases/assets/350779158'] };
export interface Tools { toolchainId: string; javaHome: string; files: { path: string; sha256: string }[] }
export async function verifyTools(root: string): Promise<Tools> {
  const tools = JSON.parse(await fs.readFile(await owned(root, 'tools/manifest.json'), 'utf8')) as Tools;
  if (![toolchainId, legacyToolchainId].includes(tools.toolchainId)) throw new Error('Managed toolchain update required. Run setup to install Temurin 8u482; existing versions and game data are retained.');
  if (typeof tools.javaHome !== 'string' || !Array.isArray(tools.files)) throw new Error('Java runtime manifest missing.');
  const javaHome = await owned(root, 'tools', tools.javaHome);
  // The previous installer also recorded Maven. Reuse its verified JDK without
  // reading, requiring or removing those obsolete build-tool files.
  const records = tools.files.filter(record => record && typeof record.path === 'string' && record.path.startsWith(tools.javaHome + '/'));
  if (records.length < 10 || ['bin/java.exe', 'bin/javaw.exe', 'jre/lib/rt.jar'].some(name => !records.some(record => record.path === tools.javaHome + '/' + name))) throw new Error('Java runtime manifest is incomplete.');
  await mapLimit(records, 8, async record => { if (!/^[a-f0-9]{64}$/.test(record.sha256) || await hash(await owned(root, 'tools', record.path)) !== record.sha256) throw new Error('Managed toolchain integrity check failed. Run setup again.'); });
  return { toolchainId, javaHome, files: records };
}
export async function installTools(root: string, signal: AbortSignal, progress: (received: number, total: number | null) => void, group?: ProcessGroup) {
  try { return await verifyTools(root); } catch { /* A new immutable installation is built; old files are retained. */ }
  const id = toolchainId + '-' + Date.now();
  const stage = await owned(root, 'tools', id); await fs.mkdir(stage, { recursive: true });
  const javaZip = await download(javaPackage, await owned(root, 'cache/jdk8u482b08.zip'), signal, progress);
  await extractZip(javaZip, path.join(stage, 'jdk'), signal);
  const jdkRoot = (await fs.readdir(path.join(stage, 'jdk')))[0];
  const javaHome = path.posix.join(id, 'jdk', jdkRoot);
  await validateJava(path.join(root, 'tools', javaHome, 'bin/java.exe'), path.join(root, 'temp'), signal, group);
  await fs.access(path.join(root, 'tools', javaHome, 'bin/javaw.exe'));
  const records = await Promise.all((await files(stage)).map(async relative => ({ path: path.posix.join(id, relative), sha256: await hash(path.join(stage, relative)) })));
  signal.throwIfAborted(); await atomicJson(await owned(root, 'tools/manifest.json'), { toolchainId, javaHome, files: records });
  return verifyTools(root);
}
export async function validateJava(java: string, temporary: string, signal?: AbortSignal, group?: ProcessGroup) {
  const javaHome = path.dirname(path.dirname(java)); await fs.mkdir(temporary, { recursive: true }); let output = '';
  const code = await runProcess(java, ['-XshowSettings:properties', '-version'], { cwd: temporary, env: childEnvironment(javaHome, temporary), signal, group, line: line => { output += line + '\n'; } });
  if (code || !/java.specification.version\s*=\s*1\.8\s/.test(output) || !/sun.arch.data.model\s*=\s*64\s/.test(output) || !/os.name\s*=\s*Windows/.test(output)) throw new Error('Select a Windows x64 Java 8 runtime.');
  return javaHome;
}
