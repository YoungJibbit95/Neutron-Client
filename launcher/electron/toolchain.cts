import fs from 'node:fs/promises';
import path from 'node:path';
import { mapLimit } from './parallel.cjs';
import { download } from './network.cjs';
import { extractZip } from './archives.cjs';
import { atomicJson, files, hash, owned } from './files.cjs';
import { childEnvironment, runProcess, type ProcessGroup } from './processes.cjs';

export const javaPackage = { url: 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip', digest: 'ea43d46ede95b51e44a12c66711706cddc762e0a766c54bccea18954e902b2aa', size: 106457258 };
export const mavenPackage = { url: 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip', algorithm: 'sha512' as const, digest: 'ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3' };
export interface Tools { javaHome: string; mavenHome: string; files: { path: string; sha256: string }[] }
export async function verifyTools(root: string): Promise<Tools> {
  const tools = JSON.parse(await fs.readFile(await owned(root, 'tools/manifest.json'), 'utf8')) as Tools;
  if (!Array.isArray(tools.files) || tools.files.length < 10) throw new Error('Toolchain manifest missing.');
  await mapLimit(tools.files, 8, async record => { if (!/^[a-f0-9]{64}$/.test(record.sha256) || await hash(await owned(root, 'tools', record.path)) !== record.sha256) throw new Error('Managed toolchain integrity check failed. Run setup again.'); });
  return { ...tools, javaHome: await owned(root, 'tools', tools.javaHome), mavenHome: await owned(root, 'tools', tools.mavenHome) };
}
export async function installTools(root: string, signal: AbortSignal, progress: (received: number, total: number | null) => void, group?: ProcessGroup) {
  try { return await verifyTools(root); } catch { /* A new immutable installation is built; old files are retained. */ }
  const id = 'jdk8u504-maven3.9.16-' + Date.now();
  const stage = await owned(root, 'tools', id); await fs.mkdir(stage, { recursive: true });
  const javaZip = await download(javaPackage, await owned(root, 'cache/jdk8u504.zip'), signal, progress);
  const mavenZip = await download(mavenPackage, await owned(root, 'cache/maven3.9.16.zip'), signal, progress);
  await extractZip(javaZip, path.join(stage, 'jdk'), signal);
  await extractZip(mavenZip, path.join(stage, 'maven'), signal);
  const jdkRoot = (await fs.readdir(path.join(stage, 'jdk')))[0];
  const mavenRoot = (await fs.readdir(path.join(stage, 'maven')))[0];
  const javaHome = path.posix.join(id, 'jdk', jdkRoot); const mavenHome = path.posix.join(id, 'maven', mavenRoot);
  await validateJava(path.join(root, 'tools', javaHome, 'bin/java.exe'), path.join(root, 'temp'), signal, group);
  await fs.access(path.join(root, 'tools', javaHome, 'bin/javac.exe'));
  const records = await Promise.all((await files(stage)).map(async relative => ({ path: path.posix.join(id, relative), sha256: await hash(path.join(stage, relative)) })));
  signal.throwIfAborted(); await atomicJson(await owned(root, 'tools/manifest.json'), { javaHome, mavenHome, files: records });
  return verifyTools(root);
}
export async function validateJava(java: string, temporary: string, signal?: AbortSignal, group?: ProcessGroup) {
  const javaHome = path.dirname(path.dirname(java)); await fs.mkdir(temporary, { recursive: true }); let output = '';
  const code = await runProcess(java, ['-XshowSettings:properties', '-version'], { cwd: temporary, env: childEnvironment(javaHome, temporary), signal, group, line: line => { output += line + '\n'; } });
  if (code || !/java.specification.version\s*=\s*1\.8\s/.test(output) || !/sun.arch.data.model\s*=\s*64\s/.test(output) || !/os.name\s*=\s*Windows/.test(output)) throw new Error('Select a Windows x64 Java 8 runtime.');
  return javaHome;
}
