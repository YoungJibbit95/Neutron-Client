import fs from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const read = async name => JSON.parse(await fs.readFile(new URL(name, root), 'utf8'));
const [release, pkg, lock] = await Promise.all(['resources/release.json', 'package.json', 'package-lock.json'].map(read));
if (process.argv.length !== 2 || release.schemaVersion !== 2 || release.channel !== 'stable' ||
    release.repository !== 'YoungJibbit95/Neutron-Client' || !/^\d+\.\d+\.\d+$/.test(release.launcherVersion) ||
    release.launcherVersion !== pkg.version || lock.version !== pkg.version || lock.packages?.['']?.version !== pkg.version ||
    Object.keys(release).sort().join(',') !== 'channel,launcherVersion,repository,schemaVersion') {
  throw new Error('Launcher package, lockfile and release catalog metadata must agree; source-build pins are no longer supported.');
}
console.log(`Validated Neutron Launcher ${pkg.version}: published client releases from ${release.repository}.`);
