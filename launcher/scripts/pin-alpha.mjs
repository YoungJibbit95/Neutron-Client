import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const file = path.join(root, 'resources/release.json');
const pkg = JSON.parse(await fs.readFile(path.join(root, 'package.json'), 'utf8'));
function validate(value) {
  if (value.schemaVersion !== 1 || !['alpha', 'stable'].includes(value.channel) || value.launcherVersion !== pkg.version ||
      !(value.channel === 'stable' ? /^\d+\.\d+\.\d+$/ : /^\d+\.\d+\.\d+-alpha\.\d+$/).test(value.launcherVersion) ||
      !/^[a-f0-9]{40}$/.test(value.clientCommit ?? '') || !/^[a-f0-9]{64}$/.test(value.sourceArchiveSha256 ?? '') ||
      value.testedClientCommit !== undefined && value.testedClientCommit !== value.clientCommit) {
    throw new Error('Set an immutable reviewed NEUTRON_CLIENT_COMMIT and NEUTRON_SOURCE_ARCHIVE_SHA256. Moving branches are not release inputs.');
  }
  return value;
}
if (process.argv.slice(2).join(' ') === '--check') {
  const value = validate(JSON.parse(await fs.readFile(file, 'utf8')));
  console.log('Pinned client: ' + value.clientCommit + ', archive SHA256 ' + value.sourceArchiveSha256);
} else if (process.argv.length === 2) {
  const value = validate({ schemaVersion: 1, channel: pkg.version.includes('-alpha.') ? 'alpha' : 'stable', launcherVersion: pkg.version,
    clientCommit: process.env.NEUTRON_CLIENT_COMMIT, sourceArchiveSha256: process.env.NEUTRON_SOURCE_ARCHIVE_SHA256,
    ...(process.env.NEUTRON_TESTED_CLIENT_COMMIT ? { testedClientCommit: process.env.NEUTRON_TESTED_CLIENT_COMMIT } : {}) });
  await fs.writeFile(file, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' });
  console.log('Generated immutable Alpha release pin. Review it before packaging.');
} else throw new Error('Supported invocation: pin-alpha.mjs [--check]');
