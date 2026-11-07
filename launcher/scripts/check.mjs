import { spawnSync } from 'node:child_process';

export function run(entry, args = []) {
  const result = spawnSync(process.execPath, [entry, ...args], { stdio: 'inherit', env: process.env, shell: false });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
run('node_modules/typescript/bin/tsc', ['--noEmit', '-p', 'tsconfig.json']);
run('node_modules/typescript/bin/tsc', ['--noEmit', '-p', 'tsconfig.electron.json']);
