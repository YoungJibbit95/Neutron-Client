import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const revision = 'a'.repeat(40);
const digest = value => createHash('sha256').update(value).digest('hex');
async function fixture(directory) {
  const name = 'Neutron-Launcher-0.3.0-Windows-x64.exe';
  const bytes = Buffer.from('Synthetic installer bytes: this fixture never executes an installer.');
  const installerSha256 = digest(bytes);
  const identity = { schemaVersion: 2, launcherVersion: '0.3.0', revision, installerSha256 };
  const client = { schemaVersion: 2, launcherVersion: '0.3.0', clientCommit: revision,
    selectedBuildId: revision + '-12345678-1234-1234-1234-123456789abc', releaseId: 42, releaseTag: 'v0.2.1', clientVersion: '0.2.1',
    clientJarSha256: 'b'.repeat(64), versionJsonSha256: 'c'.repeat(64), clientManifestSha256: 'd'.repeat(64), toolchainId: 'temurin8u482b08', verificationMs: 10 };
  const prepared = { ...client, phase: 'verified-before-activation', completedAt: '2026-11-09T16:00:00.000Z', setupMs: 20 };
  const files = {
    [name]: bytes,
    'installer-acceptance.json': JSON.stringify({ ...identity, install: 'passed', allUsersRefusal: 'passed', reinstall: 'passed; offline', uninstall: 'passed; data retained',
      releaseInstallation: 'published JAR and JSON; no client source archive or Maven repository', failureCases: 'corrupt offline cache', persistentStorage: 'retained after uninstall',
      startup: { firstWindowMs: 1, verificationMs: 2 }, clientPreparation: prepared, localVerification: client, warmPreparation: prepared }),
    'legacy-diagnostic.json': JSON.stringify({ ...identity, completed: true, ordinaryAccount: true, oldInstallationComplete: true,
      upgradeMode: 'complete-old-install', uiEvents: [{ label: 'verified-release-upgrade' }, { label: 'completed' }] }),
    'release-metadata.json': JSON.stringify({ schemaVersion: 2, launcherVersion: '0.3.0', repository: 'YoungJibbit95/Neutron-Client', sourceRevision: revision,
      workflowRunId: '123', workflowRunAttempt: '1', installer: { name, size: bytes.length, sha256: installerSha256 } }),
  };
  for (const [name, content] of Object.entries(files)) await fs.writeFile(path.join(directory, name), content);
  await fs.writeFile(path.join(directory, 'SHA256SUMS.txt'), Object.entries(files).map(([name, content]) => `${digest(content)}  ${name}\n`).join(''));
  await fs.writeFile(path.join(directory, 'release-notes.md'), 'Synthetic release notes.');
}

// Replace the CLI transport and public fetch inside a disposable Node child.
// No credentials, remote mutation, installer execution or network is involved.
async function fakeGithub() {
  const fs = await import('node:fs'); const path = await import('node:path'); const crypto = await import('node:crypto');
  const child = await import('node:child_process'); const { syncBuiltinESMExports } = await import('node:module');
  const directory = process.env.NEUTRON_TEST_ARTIFACTS, scenario = process.env.NEUTRON_TEST_SCENARIO;
  const metadata = JSON.parse(fs.readFileSync(path.join(directory, 'release-metadata.json'), 'utf8'));
  let created = false;
  const record = value => fs.appendFileSync(path.join(directory, 'calls.jsonl'), JSON.stringify(value) + '\n');
  const success = value => ({ status: 0, stdout: typeof value === 'string' ? value : JSON.stringify(value), stderr: '' });
  const missing = () => ({ status: 1, stdout: '', stderr: 'gh: Not Found (HTTP 404)' });
  const assets = ['Neutron-Launcher-0.3.0-Windows-x64.exe', 'installer-acceptance.json', 'legacy-diagnostic.json', 'release-metadata.json', 'SHA256SUMS.txt'].map(name => {
    const bytes = fs.readFileSync(path.join(directory, name));
    return { name, size: bytes.length, digest: 'sha256:' + crypto.createHash('sha256').update(bytes).digest('hex'), state: 'uploaded' };
  });
  child.default.spawnSync = (command, args) => {
    if (command !== 'gh') throw new Error('Unexpected child command.');
    record(args);
    if (args[0] === 'release' && args[1] === 'create') { created = true; return success(''); }
    if (args[0] !== 'api') throw new Error('Unexpected release mutation: ' + args.join(' '));
    const endpoint = args[1];
    if (endpoint.includes('/compare/')) return success({ status: 'identical' });
    if (endpoint.includes('/pulls?')) {
      if (scenario === 'paginated-retry' && endpoint.endsWith('page=1')) return success(Array.from({ length: 100 }, () => ({ merged_at: null })));
      return success([{ merged_at: '2026-11-09', base: { ref: 'main' }, merge_commit_sha: metadata.sourceRevision, head: { sha: '9'.repeat(40) } }]);
    }
    if (endpoint.includes('/check-runs?')) {
      if (scenario === 'paginated-retry' && endpoint.endsWith('page=1')) return success({ check_runs: Array.from({ length: 100 }, () => ({ name: 'unrelated', app: { slug: 'github-actions' } })) });
      return success({ check_runs: ['Java 8 / Windows regression', 'Repository policy', 'Dependency review'].map((name, id) => ({ name, id, app: { slug: 'github-actions' }, status: 'completed', conclusion: 'success' })) });
    }
    if (endpoint.includes('/releases/tags/')) {
      if (scenario === 'bare-tag' || scenario === 'tag-race' && !created) return missing();
      return success({ draft: scenario === 'tag-race', target_commitish: metadata.sourceRevision, assets });
    }
    if (endpoint.includes('/git/ref/tags/')) {
      if (scenario === 'tag-race' && !created) return missing();
      return success({ object: { type: 'commit', sha: ['bare-tag', 'tag-race'].includes(scenario) ? '0'.repeat(40) : metadata.sourceRevision } });
    }
    throw new Error('Unexpected GitHub endpoint: ' + endpoint);
  };
  syncBuiltinESMExports();
  globalThis.fetch = async url => {
    if (url !== 'https://github.com/YoungJibbit95/Neutron-Client/releases/download/launcher-v0.3.0/Neutron-Launcher-0.3.0-Windows-x64.exe') throw new Error('Unexpected public download.');
    record(['public-download', url]);
    return new Response(fs.readFileSync(path.join(directory, metadata.installer.name)));
  };
}

async function run(scenario) {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-publish-test-'));
  try {
    await fixture(directory);
    const preload = path.join(directory, 'fake-github.mjs');
    await fs.writeFile(preload, `await (${fakeGithub.toString()})();\n`);
    const result = spawnSync(process.execPath, ['--import', preload, fileURLToPath(new URL('./publish-release.mjs', import.meta.url)), directory], { encoding: 'utf8', timeout: 15000,
      env: { ...process.env, GH_TOKEN: '', GITHUB_ACTIONS: 'true', GITHUB_REPOSITORY: 'YoungJibbit95/Neutron-Client', GITHUB_REF: 'refs/heads/main',
        GITHUB_EVENT_NAME: 'push', GITHUB_SHA: revision, GITHUB_RUN_ID: '123', GITHUB_RUN_ATTEMPT: '2', GITHUB_STEP_SUMMARY: '', NEUTRON_TEST_ARTIFACTS: directory, NEUTRON_TEST_SCENARIO: scenario } });
    if (result.error) throw result.error;
    const calls = (await fs.readFile(path.join(directory, 'calls.jsonl'), 'utf8')).trim().split('\n').map(line => JSON.parse(line));
    return { ...result, calls };
  } finally { await fs.rm(directory, { recursive: true, force: true }); }
}

test('rejects a pre-existing wrong tag even when no release exists', async () => {
  const result = await run('bare-tag'); assert.notEqual(result.status, 0); assert.match(result.stderr, /Existing release tag points/);
  assert.equal(result.calls.some(call => call[0] === 'release'), false);
});
test('rechecks tag provenance after draft creation before uploading any bytes', async () => {
  const result = await run('tag-race'); assert.notEqual(result.status, 0); assert.match(result.stderr, /Created draft\/tag/);
  assert.deepEqual(result.calls.filter(call => call[0] === 'release').map(call => call[1]), ['create']);
});
test('retry verifies the public bytes of the same already-published accepted artifact', async () => {
  const result = await run('published-retry'); assert.equal(result.status, 0, result.stderr);
  assert.equal(result.calls.some(call => call[0] === 'release'), false); assert.equal(result.calls.some(call => call[0] === 'public-download'), true);
});
test('required PR and check evidence is found after the first GitHub API page', async () => {
  const result = await run('paginated-retry'); assert.equal(result.status, 0, result.stderr);
  assert.equal(result.calls.filter(call => call[0] === 'api' && call[1].endsWith('page=2')).length, 2);
});
