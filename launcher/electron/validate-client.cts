import fs from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { verifyDistribution } from './distribution.cjs';
import { verifyRuntime } from './minecraft.cjs';
import { owned } from './files.cjs';
import { childEnvironment, runProcess, type ProcessGroup } from './processes.cjs';
import type { Tools } from './toolchain.cjs';
import type { BuildRecord } from './contracts.cjs';

// Used for cache reuse as well as freshly built packages. No Maven, network,
// account data or personal game directory is involved in this local validation.
export async function validateClient(root: string, record: BuildRecord, tools: Tools, signal: AbortSignal, group?: ProcessGroup) {
  const client = await verifyDistribution(root, record);
  const runtime = await verifyRuntime(root, client.manifest.runtime);
  const temporary = await owned(root, 'temp', 'bootstrap-' + randomUUID()); await fs.mkdir(temporary, { recursive: true });
  const code = await runProcess(path.join(tools.javaHome, 'bin/java.exe'), [
    '-Xms32M', '-Xmx256M', '-Djava.io.tmpdir=' + temporary,
    '-Djava.library.path=' + path.join(runtime.directory, 'natives'), '-cp',
    [...runtime.manifest.javaLibraries.map(name => path.join(runtime.directory, name)), ...client.classpath].join(path.delimiter),
    client.manifest.entryPoint, '--gameDir', temporary, '--assetsDir', path.join(runtime.directory, 'assets'),
    '--nativesDir', path.join(runtime.directory, 'natives'), '--assetIndex', '1.8', '--version', client.manifest.version,
    '--username', 'NeutronPlayer', '--validateOnly',
  ], { cwd: temporary, env: childEnvironment(tools.javaHome, temporary), signal, group, line: () => {} });
  if (code !== 0) throw new Error('Cached Neutron Bootstrap validation failed (exit ' + code + '). Previous version retained.');
  signal.throwIfAborted();
}
