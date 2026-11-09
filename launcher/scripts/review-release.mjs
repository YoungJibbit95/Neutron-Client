import fs from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const digest = /^[a-f0-9]{64}$/;
const finiteTime = value => Number.isFinite(value) && value >= 0;
export const readJson = async file => JSON.parse((await fs.readFile(file, 'utf8')).replace(/^\uFEFF/, ''));
export async function sha256(file) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

// This independent job reads receipts and bytes only. It never executes the
// accepted installer or trusts a filename as proof of installer acceptance.
export function verifyEvidence({ version, revision, installerSha256, cold, upgrade }) {
  if (!/^\d+\.\d+\.\d+$/.test(version) || !/^[a-f0-9]{40}$/.test(revision) || !digest.test(installerSha256)) throw new Error('Invalid release identity.');
  for (const receipt of [cold, upgrade]) {
    if (receipt?.schemaVersion !== 2 || receipt.launcherVersion !== version || receipt.revision !== revision || receipt.installerSha256?.toLowerCase() !== installerSha256) throw new Error('Acceptance does not identify this exact installer and source revision.');
  }
  if (cold.install !== 'passed' || cold.allUsersRefusal !== 'passed' || !cold.reinstall?.startsWith('passed;') || !cold.uninstall?.startsWith('passed;') ||
      cold.releaseInstallation !== 'published JAR and JSON; no client source archive or Maven repository' ||
      !cold.failureCases?.includes('corrupt offline cache') || !cold.persistentStorage?.includes('retained after uninstall') ||
      !finiteTime(cold.startup?.firstWindowMs) || !finiteTime(cold.startup?.verificationMs)) throw new Error('Windows lifecycle acceptance is incomplete.');
  const first = cold.clientPreparation;
  for (const checked of [first, cold.localVerification, cold.warmPreparation]) {
    if (checked?.schemaVersion !== 2 || checked.launcherVersion !== version || checked.toolchainId !== 'temurin8u482b08' ||
        !/^[a-f0-9]{40}$/.test(checked.clientCommit) || !new RegExp('^' + checked.clientCommit + '-[a-f0-9-]{36}$').test(checked.selectedBuildId) ||
        !Number.isSafeInteger(checked.releaseId) || checked.releaseId < 1 || !/^v\d+\.\d+\.\d+$/.test(checked.releaseTag) ||
        checked.clientVersion !== checked.releaseTag.slice(1) || !digest.test(checked.clientJarSha256) || !digest.test(checked.versionJsonSha256) ||
        !digest.test(checked.clientManifestSha256) || !finiteTime(checked.verificationMs)) throw new Error('Release client verification receipt is invalid.');
    for (const key of ['clientCommit', 'selectedBuildId', 'releaseId', 'releaseTag', 'clientVersion', 'clientJarSha256', 'versionJsonSha256', 'clientManifestSha256']) {
      if (checked[key] !== first[key]) throw new Error('Offline acceptance did not verify the same selected release assets.');
    }
  }
  for (const checked of [first, cold.warmPreparation]) {
    if (checked.phase !== 'verified-before-activation' || !Number.isFinite(Date.parse(checked.completedAt)) || !finiteTime(checked.setupMs)) throw new Error('Installation activation receipt is incomplete.');
  }
  if (upgrade.completed !== true || upgrade.ordinaryAccount !== true || typeof upgrade.oldInstallationComplete !== 'boolean' ||
      upgrade.upgradeMode !== (upgrade.oldInstallationComplete ? 'complete-old-install' : 'repair-incomplete-old-install') ||
      !upgrade.uiEvents?.some(event => event.label === 'verified-release-upgrade') || !upgrade.uiEvents?.some(event => event.label === 'completed')) throw new Error('Legacy installation upgrade/repair acceptance is incomplete.');
  return { client: first, upgradeMode: upgrade.upgradeMode };
}

async function collect(directory) {
  const result = [];
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error('Linked artifact entries are not accepted.');
    if (entry.isDirectory()) result.push(...await collect(file));
    else if (entry.isFile()) result.push(file);
    else throw new Error('Unsupported artifact entry.');
  }
  return result;
}

export async function reviewRelease(input, output, revision, runId, runAttempt) {
  const config = await readJson(new URL('../resources/release.json', import.meta.url));
  const version = config.launcherVersion;
  if (config.schemaVersion !== 2 || config.repository !== 'YoungJibbit95/Neutron-Client' || config.channel !== 'stable' || !/^\d+$/.test(runId ?? '') || !/^\d+$/.test(runAttempt ?? '')) throw new Error('Invalid source release metadata or workflow identity.');
  const entries = await collect(path.resolve(input));
  const one = name => {
    const found = entries.filter(file => path.basename(file) === name);
    if (found.length !== 1) throw new Error('Exactly one accepted artifact is required: ' + name);
    return found[0];
  };
  const installerName = `Neutron-Launcher-${version}-Windows-x64.exe`;
  const installer = one(installerName), coldFile = one('installer-acceptance.json'), upgradeFile = one('legacy-diagnostic.json');
  const installerSha256 = await sha256(installer);
  const cold = await readJson(coldFile), upgrade = await readJson(upgradeFile);
  const checked = verifyEvidence({ version, revision, installerSha256, cold, upgrade });
  await fs.mkdir(output, { recursive: false });
  for (const file of [installer, coldFile, upgradeFile]) await fs.copyFile(file, path.join(output, path.basename(file)), 1);
  if (await sha256(path.join(output, installerName)) !== installerSha256) throw new Error('Accepted installer changed during artifact transfer.');
  const metadata = { schemaVersion: 2, launcherVersion: version, repository: config.repository, sourceRevision: revision,
    workflowRunId: runId, workflowRunAttempt: runAttempt, installer: { name: installerName, size: (await fs.stat(installer)).size, sha256: installerSha256 },
    client: checked.client, validation: { windowsLifecycle: 'passed', offlineReleaseReuse: 'passed', upgradeMode: checked.upgradeMode, independentArtifactReview: 'passed' } };
  await fs.writeFile(path.join(output, 'release-metadata.json'), JSON.stringify(metadata, null, 2) + '\n');
  const assets = [installerName, 'installer-acceptance.json', 'legacy-diagnostic.json', 'release-metadata.json'];
  await fs.writeFile(path.join(output, 'SHA256SUMS.txt'), (await Promise.all(assets.map(async name => `${await sha256(path.join(output, name))}  ${name}\n`))).join(''));
  const migration = checked.upgradeMode === 'complete-old-install' ? 'Upgrade from an installed 0.2.0 client passed.' : 'Repair and migration of a registered but incomplete 0.2.0 installation passed; a complete old source-build installation is not claimed.';
  const notes = `# Neutron Launcher ${version}\n\n` +
    'Downloads and starts published Neutron client JAR/JSON releases directly from GitHub. The client source-build installation path, Maven download and source adapter have been removed.\n\n' +
    '- Lists supported stable client releases, with refresh and version selection.\n- Installs verified release assets and starts installed versions locally.\n- Retains settings, game data and existing client versions during installation and updates.\n\n' +
    `Windows x64, current-user installation. First setup requires internet and sufficient space on the selected data drive. The installer is unsigned.\n\n` +
    `Acceptance: standard-user cold install, local/offline verification, renderer startup, failure preservation, offline reinstall and uninstall passed for this exact EXE. ${migration}\n\n` +
    `Client used during acceptance: ${checked.client.releaseTag}. Source: \`${revision}\`. [Build and acceptance](https://github.com/${config.repository}/actions/runs/${runId}). Checksums and full receipts are attached. No new manual gameplay, full theme/DPI or GPU-loss acceptance is claimed.\n`;
  await fs.writeFile(path.join(output, 'release-notes.md'), notes);
  return metadata;
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 4) throw new Error('Usage: review-release.mjs <downloaded-candidate-directory> <new-reviewed-directory>');
  const result = await reviewRelease(process.argv[2], process.argv[3], process.env.GITHUB_SHA, process.env.GITHUB_RUN_ID, process.env.GITHUB_RUN_ATTEMPT);
  console.log(`Accepted ${result.installer.name}: ${result.installer.sha256}`);
}
