import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyEvidence } from './review-release.mjs';

function fixture() {
  const base = { schemaVersion: 2, launcherVersion: '0.3.0', clientCommit: 'a'.repeat(40), selectedBuildId: 'a'.repeat(40) + '-12345678-1234-1234-1234-123456789abc',
    releaseId: 42, releaseTag: 'v0.2.1', clientVersion: '0.2.1', clientJarSha256: 'b'.repeat(64), versionJsonSha256: 'c'.repeat(64), clientManifestSha256: 'd'.repeat(64), toolchainId: 'temurin8u482b08', verificationMs: 15 };
  const prepared = { ...base, phase: 'verified-before-activation', completedAt: '2026-11-09T16:00:00.000Z', setupMs: 200 };
  const identity = { schemaVersion: 2, launcherVersion: '0.3.0', revision: 'e'.repeat(40), installerSha256: 'f'.repeat(64) };
  return { version: '0.3.0', revision: identity.revision, installerSha256: identity.installerSha256,
    cold: { ...identity, install: 'passed', allUsersRefusal: 'passed', reinstall: 'passed; all launcher networking denied', uninstall: 'passed; synthetic personal data retained',
      releaseInstallation: 'published JAR and JSON; no client source archive or Maven repository', failureCases: 'corrupt offline cache', persistentStorage: 'retained after uninstall',
      startup: { firstWindowMs: 80, verificationMs: 40 }, clientPreparation: prepared, localVerification: { ...base }, warmPreparation: { ...prepared } },
    upgrade: { ...identity, completed: true, ordinaryAccount: true, oldInstallationComplete: true, upgradeMode: 'complete-old-install', uiEvents: [{ label: 'verified-release-upgrade' }, { label: 'completed' }] } };
}

test('accepts matching exact installer, release assets and complete Windows lifecycle', () => {
  assert.equal(verifyEvidence(fixture()).upgradeMode, 'complete-old-install');
});
test('reports incomplete historical install as repair rather than full upgrade', () => {
  const input = fixture(); input.upgrade.oldInstallationComplete = false; input.upgrade.upgradeMode = 'repair-incomplete-old-install';
  assert.equal(verifyEvidence(input).upgradeMode, 'repair-incomplete-old-install');
});
for (const [name, change] of [
  ['different installer bytes', input => { input.installerSha256 = '0'.repeat(64); }],
  ['stale source receipt', input => { input.cold.revision = '0'.repeat(40); }],
  ['failed lifecycle', input => { input.cold.install = 'failed'; }],
  ['missing renderer handshake', input => { input.cold.startup = {}; }],
  ['changed offline JAR', input => { input.cold.localVerification.clientJarSha256 = '0'.repeat(64); }],
  ['unverified release JSON', input => { delete input.cold.clientPreparation.versionJsonSha256; }],
  ['missing atomic activation', input => { delete input.cold.warmPreparation.phase; }],
  ['wrong client release version', input => { input.cold.localVerification.clientVersion = '0.2.0'; }],
  ['incomplete upgrade', input => { input.upgrade.completed = false; }],
  ['mislabelled incomplete old install', input => { input.upgrade.oldInstallationComplete = false; }],
]) test('rejects ' + name, () => { const input = fixture(); change(input); assert.throws(() => verifyEvidence(input)); });
