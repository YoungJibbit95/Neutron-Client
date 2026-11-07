import { run } from './check.mjs';
import path from 'node:path';
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
run('node_modules/electron-builder/cli.js', ['--config', 'electron-builder.cjs', '--win', 'nsis', '--x64', '--publish', 'never']);
