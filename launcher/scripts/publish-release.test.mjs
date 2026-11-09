import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

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
  await fs.writeFile(path.join(directory, 'release-notes.md'), `# Neutron Launcher 0.3.0\n\nSource: \`${revision}\`. [Build and acceptance](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/123).\n`);
}

// Replace the CLI transport and public fetch inside a disposable Node child.
// No credentials, remote mutation, installer execution or network is involved.
async function fakeGithub() {
  const fs = await import('node:fs'); const path = await import('node:path'); const crypto = await import('node:crypto');
  const child = await import('node:child_process'); const { syncBuiltinESMExports } = await import('node:module');
  const directory = process.env.NEUTRON_TEST_ARTIFACTS, scenario = process.env.NEUTRON_TEST_SCENARIO;
  const metadata = JSON.parse(fs.readFileSync(path.join(directory, 'release-metadata.json'), 'utf8'));
  const previous = '8'.repeat(40), recovering = scenario.startsWith('retarget-');
  let created = false, reads = 0, tagReads = 0;
  const record = value => fs.appendFileSync(path.join(directory, 'calls.jsonl'), JSON.stringify(value) + '\n');
  const success = value => ({ status: 0, stdout: typeof value === 'string' ? value : JSON.stringify(value), stderr: '' });
  const missing = () => ({ status: 1, stdout: '', stderr: 'gh: Not Found (HTTP 404)' });
  const assets = ['Neutron-Launcher-0.3.0-Windows-x64.exe', 'installer-acceptance.json', 'legacy-diagnostic.json', 'release-metadata.json', 'SHA256SUMS.txt'].map(name => {
    const bytes = fs.readFileSync(path.join(directory, name));
    return { name, size: bytes.length, digest: 'sha256:' + crypto.createHash('sha256').update(bytes).digest('hex'), state: 'uploaded' };
  });
  const notes = source => '# Neutron Launcher 0.3.0\n\nSource: `' + source + '`. [Build and acceptance](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/122).\n';
  const newDraft = source => ({ id: 7, tag_name: 'launcher-v0.3.0', draft: true, published_at: null,
    name: 'Neutron Launcher 0.3.0', author: { login: 'github-actions[bot]', type: 'Bot' },
    target_commitish: source, assets: [], body: notes(source) });
  let release = ['bare-tag', 'tag-race', 'new-draft', 'upload-mismatch'].includes(scenario) ? null : newDraft(recovering ? previous : metadata.sourceRevision);
  const published = ['published-retry', 'paginated-retry', 'published-files-differ'].includes(scenario);
  if (published) Object.assign(release, { draft: false, published_at: '2026-11-09', assets });
  if (scenario === 'published-files-differ') release.assets = [{ ...assets[0], size: 1 }];
  if (scenario === 'retarget-filled') release.assets = [assets[0]];
  if (scenario === 'retarget-foreign-author') release.author = { login: 'someone-else', type: 'User' };
  if (scenario === 'retarget-foreign-notes') release.body = notes('7'.repeat(40));
  if (scenario === 'retarget-branch-target') release.target_commitish = 'main';
  let tag = published || scenario === 'retarget-existing-tag' ? metadata.sourceRevision : null;
  if (scenario === 'bare-tag') tag = '0'.repeat(40);
  child.default.spawnSync = (command, args) => {
    if (command !== 'gh') throw new Error('Unexpected child command.');
    record(args);
    if (args[0] === 'release') {
      if (args[1] === 'create') {
        if (release) throw new Error('Duplicate draft creation.');
        created = true; release = newDraft(metadata.sourceRevision); return success('');
      }
      if (!release) throw new Error('Mutation without a release.');
      if (args[1] === 'edit') {
        if (args.includes('--target')) release.target_commitish = args[args.indexOf('--target') + 1];
        if (args.includes('--notes-file')) release.body = fs.readFileSync(args[args.indexOf('--notes-file') + 1], 'utf8');
        if (args.includes('--draft=true')) release.draft = true;
        if (args.includes('--draft=false')) { release.draft = false; release.published_at = '2026-11-09'; tag = release.target_commitish; }
        return success('');
      }
      if (args[1] === 'upload') {
        release.assets = scenario === 'upload-mismatch' ? [{ ...assets[0], digest: 'sha256:' + '0'.repeat(64) }, ...assets.slice(1)] : assets;
        return success('');
      }
    }
    if (args[0] !== 'api') throw new Error('Unexpected release mutation: ' + args.join(' '));
    const endpoint = args[1];
    if (endpoint.includes('/compare/')) {
      if (endpoint.endsWith('...main')) return success({ status: 'identical' });
      return success({ status: scenario === 'retarget-unrelated' ? 'diverged' : 'ahead',
        base_commit: { sha: scenario === 'retarget-wrong-base' ? '7'.repeat(40) : previous },
        merge_base_commit: { sha: scenario === 'retarget-unrelated' ? '7'.repeat(40) : previous } });
    }
    if (endpoint.includes('/pulls?')) {
      if (scenario === 'paginated-retry' && endpoint.endsWith('page=1')) return success(Array.from({ length: 100 }, () => ({ merged_at: null })));
      return success([{ merged_at: '2026-11-09', base: { ref: 'main' }, merge_commit_sha: metadata.sourceRevision, head: { sha: '9'.repeat(40) } }]);
    }
    if (endpoint.includes('/check-runs?')) {
      if (scenario === 'paginated-retry' && endpoint.endsWith('page=1')) return success({ check_runs: Array.from({ length: 100 }, () => ({ name: 'unrelated', app: { slug: 'github-actions' } })) });
      return success({ check_runs: ['Java 8 / Windows regression', 'Repository policy', 'Dependency review'].map((name, id) => ({ name, id, app: { slug: 'github-actions' }, status: 'completed', conclusion: 'success' })) });
    }
    // GitHub's tag endpoint does not expose unpublished drafts.
    if (endpoint.includes('/releases/tags/')) return release && !release.draft ? success(release) : missing();
    if (endpoint.includes('/releases?')) {
      if (scenario === 'paginated-draft' && endpoint.endsWith('page=1')) return success(Array.from({ length: 100 }, (_, id) => ({ id: id + 100, tag_name: 'unrelated-' + id })));
      if (scenario === 'duplicate-drafts') return success([release, { ...release, id: 8 }]);
      return success(release ? [release] : []);
    }
    if (endpoint.endsWith('/releases/7')) {
      reads++;
      if (scenario === 'wrong-release-identity') return success({ ...release, id: 8 });
      if (recovering && reads === 2) {
        if (scenario === 'retarget-assets-race') release.assets = [assets[0]];
        if (scenario === 'retarget-target-race') release.target_commitish = '7'.repeat(40);
        if (scenario === 'retarget-published-race') Object.assign(release, { draft: false, published_at: '2026-11-09' });
      }
      return release ? success(release) : missing();
    }
    if (endpoint.includes('/git/ref/tags/')) {
      tagReads++;
      if (scenario === 'tag-race' && created) return success({ object: { type: 'commit', sha: '0'.repeat(40) } });
      if (scenario === 'retarget-tag-race' && tagReads >= 2) return success({ object: { type: 'commit', sha: metadata.sourceRevision } });
      return tag ? success({ object: { type: 'commit', sha: tag } }) : missing();
    }
    throw new Error('Unexpected GitHub endpoint: ' + endpoint);
  };
  syncBuiltinESMExports();
  globalThis.fetch = async url => {
    if (url !== 'https://github.com/YoungJibbit95/Neutron-Client/releases/download/launcher-v0.3.0/Neutron-Launcher-0.3.0-Windows-x64.exe') throw new Error('Unexpected public download.');
    if (!release || release.draft) throw new Error('Public download attempted before publication.');
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
    const result = spawnSync(process.execPath, ['--import', pathToFileURL(preload).href, fileURLToPath(new URL('./publish-release.mjs', import.meta.url)), directory], { encoding: 'utf8', timeout: 15000,
      env: { ...process.env, GH_TOKEN: '', GITHUB_ACTIONS: 'true', GITHUB_REPOSITORY: 'YoungJibbit95/Neutron-Client', GITHUB_REF: 'refs/heads/main',
        GITHUB_EVENT_NAME: 'push', GITHUB_SHA: revision, GITHUB_RUN_ID: '123', GITHUB_RUN_ATTEMPT: '2', GITHUB_STEP_SUMMARY: '', NEUTRON_TEST_ARTIFACTS: directory, NEUTRON_TEST_SCENARIO: scenario } });
    if (result.error) throw result.error;
    let captured;
    try { captured = await fs.readFile(path.join(directory, 'calls.jsonl'), 'utf8'); }
    catch (error) {
      if (error.code !== 'ENOENT') throw error;
      throw new Error(`Publisher test child stopped before its first GitHub call (exit ${result.status}).\n${result.stderr || result.stdout || 'No child output.'}`, { cause: error });
    }
    const calls = captured.trim().split('\n').map(line => JSON.parse(line));
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
for (const scenario of ['new-draft', 'existing-draft', 'paginated-draft']) {
  test(`${scenario}: unpublished drafts are read by ID and published with the accepted bytes`, async () => {
    const result = await run(scenario); assert.equal(result.status, 0, result.stderr);
    assert.equal(result.calls.some(call => call[0] === 'api' && call[1].includes('/releases/tags/')), false);
    assert.ok(result.calls.filter(call => call[0] === 'api' && call[1].endsWith('/releases/7')).length >= 2);
    assert.equal(result.calls.some(call => call[0] === 'release' && call[1] === 'create'), scenario === 'new-draft');
    assert.equal(result.calls.some(call => call[0] === 'public-download'), true);
    if (scenario === 'paginated-draft') assert.ok(result.calls.some(call => call[1].endsWith('/releases?per_page=100&page=2')));
  });
}
for (const scenario of ['duplicate-drafts', 'wrong-release-identity', 'published-files-differ']) {
  test(`${scenario}: inconsistent release identity or bytes are retained without mutation`, async () => {
    const result = await run(scenario); assert.notEqual(result.status, 0);
    assert.equal(result.calls.some(call => call[0] === 'release'), false);
  });
}
test('retargets only an empty owned untagged draft from a proven ancestor and then verifies the public bytes', async () => {
  const result = await run('retarget-empty'); assert.equal(result.status, 0, result.stderr);
  const edits = result.calls.filter(call => call[0] === 'release' && call.includes('--target'));
  assert.equal(edits.length, 1); assert.ok(edits[0].includes('--draft=true'));
  assert.equal(edits[0][edits[0].indexOf('--target') + 1], revision);
  assert.ok(result.calls.some(call => call[0] === 'api' && call[1].endsWith(`/compare/${'8'.repeat(40)}...${revision}`)));
  assert.equal(result.calls.some(call => call[0] === 'release' && call[1] === 'create'), false);
  assert.equal(result.calls.some(call => call[0] === 'public-download'), true);
});
for (const scenario of ['retarget-filled', 'retarget-foreign-author', 'retarget-foreign-notes', 'retarget-branch-target',
  'retarget-existing-tag', 'retarget-unrelated', 'retarget-wrong-base', 'retarget-assets-race', 'retarget-target-race',
  'retarget-published-race', 'retarget-tag-race']) {
  test(`${scenario}: unsafe draft recovery stops before any mutation`, async () => {
    const result = await run(scenario); assert.notEqual(result.status, 0);
    assert.equal(result.calls.some(call => call[0] === 'release'), false);
  });
}
test('draft API correction still rejects changed uploaded bytes before publication', async () => {
  const result = await run('upload-mismatch'); assert.notEqual(result.status, 0); assert.match(result.stderr, /Uploaded draft assets/);
  assert.equal(result.calls.some(call => call.includes('--draft=false')), false);
  assert.equal(result.calls.some(call => call[0] === 'public-download'), false);
});
