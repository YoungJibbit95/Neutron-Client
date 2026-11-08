import { run } from './check.mjs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { existsSync, lstatSync, realpathSync } from 'node:fs';
for (const key of ['TEMP', 'TMP', 'ELECTRON_CACHE', 'ELECTRON_BUILDER_CACHE', 'NEUTRON_PACKAGE_OUTPUT']) {
  if (!process.env[key] || !path.isAbsolute(process.env[key])) throw new Error('Set an absolute ' + key + ' on the chosen build drive.');
}
const output = path.resolve(process.env.NEUTRON_PACKAGE_OUTPUT);
if (output === path.parse(output).root) throw new Error('A drive root is not a package output directory.');
for (const target of [output, path.join(output, 'win-unpacked')]) {
  if (existsSync(target) && (lstatSync(target).isSymbolicLink() || realpathSync(target).toLowerCase() !== target.toLowerCase())) throw new Error('Refusing a redirected package output directory.');
}
run('scripts/pin-alpha.mjs', ['--check']);
process.env.CSC_IDENTITY_AUTO_DISCOVERY = 'false';
// Only retry a confirmed network 504. Keep electron-builder's own pinned
// binary SHA checks and the same NSIS script; never turn compilation failures
// into successful candidates or relax the installed-app acceptance test.
const args = ['node_modules/electron-builder/cli.js', '--config', 'electron-builder.cjs', '--win', 'nsis', '--x64', '--publish', 'never'];
function packageOnce(env) {
  const result = spawnSync(process.execPath, args, { cwd: process.cwd(), env, shell: false, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error) throw result.error;
  return { code: result.status ?? 1, output: (result.stdout ?? '') + '\n' + (result.stderr ?? '') };
}
let attempt = packageOnce(process.env);
if (attempt.code !== 0 && process.env.GITHUB_ACTIONS === 'true' && /(?:Response code (?:502|503|504)|HTTP (?:502|503|504)|Bad Gateway|Service Unavailable|Gateway Time-out)/i.test(attempt.output)) {
  console.error('GitHub Electron/NSIS binary CDN returned 502/503/504. Retrying with Electron and NSIS mirrors; build remains subject to package integrity checks and lifecycle acceptance.');
  attempt = packageOnce({ ...process.env,
    ELECTRON_MIRROR: 'https://npmmirror.com/mirrors/electron/',
    ELECTRON_BUILDER_BINARIES_MIRROR: 'https://npmmirror.com/mirrors/electron-builder-binaries/'
  });
}
if (attempt.code !== 0) process.exit(attempt.code);
