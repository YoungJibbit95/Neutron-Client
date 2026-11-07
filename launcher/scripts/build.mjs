import { run } from './check.mjs';
import { existsSync, lstatSync, realpathSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Vite may clear only this prototype's own generated dist/, never a linked target.
const root = realpathSync(fileURLToPath(new URL('../', import.meta.url)));
const output = path.join(root, 'dist');
const same = (a, b) => process.platform === 'win32' ? a.toLowerCase() === b.toLowerCase() : a === b;
if (!same(realpathSync(process.cwd()), root)) throw new Error('Run the build from launcher/.');
if (existsSync(output) && (lstatSync(output).isSymbolicLink() || !same(realpathSync(output), output))) {
  throw new Error('Refusing a linked or redirected prototype dist directory.');
}
console.log(`Verified generated output directory: ${output}`);
run('node_modules/vite/bin/vite.js', ['build']);
run('node_modules/typescript/bin/tsc', ['-p', 'tsconfig.electron.json']);
