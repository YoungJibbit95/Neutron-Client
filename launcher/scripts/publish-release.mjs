import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { readJson, sha256, verifyEvidence } from './review-release.mjs';

const repository = 'YoungJibbit95/Neutron-Client';
function gh(args, optional404 = false) {
  const result = spawnSync('gh', args, { encoding: 'utf8', shell: false, maxBuffer: 8 * 1024 * 1024 });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    if (optional404 && /HTTP 404/.test(result.stderr ?? '')) return null;
    throw new Error('GitHub release operation failed: ' + (result.stderr ?? '').trim());
  }
  return result.stdout;
}
const api = (endpoint, optional404 = false) => {
  const value = gh(['api', `repos/${repository}/${endpoint}`], optional404);
  return value === null ? null : JSON.parse(value);
};
function pages(endpoint, key) {
  const values = [];
  for (let page = 1; page <= 100; page++) {
    const response = api(`${endpoint}?per_page=100&page=${page}`);
    const items = key ? response[key] : response;
    if (!Array.isArray(items)) throw new Error('Invalid GitHub evidence page.');
    values.push(...items);
    if (items.length < 100) return values;
  }
  throw new Error('GitHub evidence pagination exceeded its supported bound.');
}
function releaseById(id, tag) {
  if (!Number.isSafeInteger(id) || id < 1) throw new Error('Release must have a concrete GitHub ID.');
  const release = api(`releases/${id}`);
  if (release.id !== id || release.tag_name !== tag) throw new Error('Release identity changed while reading the draft.');
  return release;
}
function findRelease(tag) {
  // The tag endpoint only returns published releases. Authenticated listing
  // includes drafts; bind subsequent reads to the one concrete release ID.
  const matches = pages('releases').filter(release => release.tag_name === tag);
  if (matches.length > 1) throw new Error('Multiple releases use the requested tag; all were retained.');
  return matches.length ? releaseById(matches[0].id, tag) : null;
}
function ownedDraft(release, version, source) {
  const body = typeof release.body === 'string' ? release.body : '';
  const sources = [...body.matchAll(/Source: `([a-f0-9]{40})`\./g)];
  const runs = [...body.matchAll(/\[Build and acceptance\]\(https:\/\/github\.com\/YoungJibbit95\/Neutron-Client\/actions\/runs\/([1-9]\d*)\)/g)];
  return release.draft === true && release.published_at === null && Array.isArray(release.assets) &&
    release.author?.login === 'github-actions[bot]' && release.author?.type === 'Bot' &&
    release.name === `Neutron Launcher ${version}` && body.startsWith(`# Neutron Launcher ${version}\n`) &&
    sources.length === 1 && sources[0][1] === source && runs.length === 1;
}
function tagRevision(tag) {
  let ref = api(`git/ref/tags/${tag}`, true)?.object;
  if (!ref) return null;
  for (let depth = 0; ref.type === 'tag' && depth < 4; depth++) ref = api(`git/tags/${ref.sha}`).object;
  if (ref.type !== 'commit' || !/^[a-f0-9]{40}$/.test(ref.sha)) throw new Error('Release tag must resolve to a concrete commit.');
  return ref.sha;
}
const directory = process.argv[2];
const revision = process.env.GITHUB_SHA;
if (process.argv.length !== 3 || process.env.GITHUB_ACTIONS !== 'true' || process.env.GITHUB_REPOSITORY !== repository ||
    process.env.GITHUB_REF !== 'refs/heads/main' || !['push', 'workflow_dispatch'].includes(process.env.GITHUB_EVENT_NAME ?? '') ||
    !/^[a-f0-9]{40}$/.test(revision ?? '')) throw new Error('Publication requires an authorized main-branch workflow and a concrete source revision.');
const metadata = await readJson(path.join(directory, 'release-metadata.json'));
const version = metadata.launcherVersion;
const name = `Neutron-Launcher-${version}-Windows-x64.exe`;
const tag = `launcher-v${version}`;
if (metadata.schemaVersion !== 2 || metadata.repository !== repository || !/^\d+\.\d+\.\d+$/.test(version ?? '') ||
    metadata.sourceRevision !== revision || metadata.workflowRunId !== process.env.GITHUB_RUN_ID ||
    !Number.isSafeInteger(Number(metadata.workflowRunAttempt)) || Number(metadata.workflowRunAttempt) < 1 ||
    Number(metadata.workflowRunAttempt) > Number(process.env.GITHUB_RUN_ATTEMPT) || metadata.installer?.name !== name) throw new Error('Reviewed artifact provenance does not match this main workflow.');
const names = [name, 'installer-acceptance.json', 'legacy-diagnostic.json', 'release-metadata.json', 'SHA256SUMS.txt'];
const digests = new Map();
for (const asset of names) {
  const file = path.join(directory, asset);
  const stat = await fs.lstat(file);
  if (!stat.isFile() || stat.isSymbolicLink()) throw new Error('Release asset must be a regular reviewed file.');
  digests.set(asset, { sha256: await sha256(file), size: stat.size });
}
if (digests.get(name).sha256 !== metadata.installer.sha256 || digests.get(name).size !== metadata.installer.size) throw new Error('Reviewed installer bytes changed before publication.');
verifyEvidence({ version, revision, installerSha256: metadata.installer.sha256,
  cold: await readJson(path.join(directory, 'installer-acceptance.json')), upgrade: await readJson(path.join(directory, 'legacy-diagnostic.json')) });
const checksumLines = (await fs.readFile(path.join(directory, 'SHA256SUMS.txt'), 'utf8')).trim().split('\n');
if (checksumLines.length !== names.length - 1 || names.slice(0, -1).some(asset => !checksumLines.includes(`${digests.get(asset).sha256}  ${asset}`))) throw new Error('Reviewed release checksums do not match the uploaded files.');

// The protected-branch merge is retained as the release approval boundary.
// Neither this job nor its token updates branch protection or pushes to main.
const comparison = api(`compare/${revision}...main`);
if (!['ahead', 'identical'].includes(comparison.status)) throw new Error('Release source is no longer an ancestor of main.');
const merged = pages(`commits/${revision}/pulls`).filter(pr => pr.merged_at && pr.base?.ref === 'main' && pr.merge_commit_sha === revision);
if (merged.length !== 1) throw new Error('Release source must identify one normally merged main pull request.');
const checks = pages(`commits/${merged[0].head.sha}/check-runs`, 'check_runs');
for (const required of ['Java 8 / Windows regression', 'Repository policy', 'Dependency review']) {
  const check = checks.filter(item => item.name === required && item.app?.slug === 'github-actions').sort((a, b) => b.id - a.id)[0];
  if (!check || check.status !== 'completed' || check.conclusion !== 'success') throw new Error('Required merged-PR check has not passed: ' + required);
}
const existing = findRelease(tag);
// --target only creates a missing tag; it never moves an existing tag. Check
// the ref even when there is no release object, before any release mutation.
const priorTagRevision = tagRevision(tag);
if (priorTagRevision && priorTagRevision !== revision) throw new Error('Existing release tag points to a different source revision; it was retained.');
const matchingAssets = release => Array.isArray(release.assets) && release.assets.length === names.length && !names.some(asset => {
  const remote = release.assets.filter(item => item.name === asset);
  return remote.length !== 1 || remote[0].state !== 'uploaded' || remote[0].size !== digests.get(asset).size || remote[0].digest !== 'sha256:' + digests.get(asset).sha256;
});
if (existing && !existing.draft) {
  if (priorTagRevision !== revision || !matchingAssets(existing)) throw new Error('This launcher version is already published with different accepted files. Use a new version; published files were retained.');
  console.log(`Rechecking the public bytes of already-published ${tag}; no release files were changed.`);
} else {
  if (existing) {
    const oldTarget = existing.target_commitish;
    if (!ownedDraft(existing, version, oldTarget)) throw new Error('Existing draft has foreign or incomplete provenance; it was retained.');
    const retarget = oldTarget !== revision;
    if (retarget) {
      if (priorTagRevision || existing.assets.length !== 0 || !/^[a-f0-9]{40}$/.test(oldTarget ?? '')) throw new Error('Only an empty, untagged draft with a concrete previous source can be retargeted.');
      const ancestor = api(`compare/${oldTarget}...${revision}`);
      if (!['ahead', 'identical'].includes(ancestor.status) || ancestor.base_commit?.sha !== oldTarget || ancestor.merge_base_commit?.sha !== oldTarget) throw new Error('Previous draft source is not a verified ancestor of the accepted source.');
    } else if (existing.assets.length !== 0 && !matchingAssets(existing)) {
      throw new Error('Existing draft contains different or incomplete files; they were retained.');
    }
    // Recheck mutable draft state and tag absence immediately before recovery.
    const fresh = releaseById(existing.id, tag);
    if (fresh.target_commitish !== oldTarget || !ownedDraft(fresh, version, oldTarget) ||
        (retarget ? fresh.assets.length !== 0 || tagRevision(tag) !== null : fresh.assets.length !== 0 && !matchingAssets(fresh))) throw new Error('Existing draft changed before recovery; it was retained.');
    const edit = ['release', 'edit', tag, '--repo', repository, '--draft=true', '--notes-file', path.join(directory, 'release-notes.md')];
    if (retarget) edit.push('--target', revision);
    gh(edit);
    if (retarget) console.log(`Retargeted empty unpublished draft ${existing.id} from ${oldTarget} to accepted source ${revision}.`);
  } else {
    gh(['release', 'create', tag, '--repo', repository, '--target', revision, '--draft', '--title', `Neutron Launcher ${version}`, '--notes-file', path.join(directory, 'release-notes.md')]);
  }
  const created = existing ? releaseById(existing.id, tag) : findRelease(tag);
  const createdTagRevision = tagRevision(tag);
  if (!created || !ownedDraft(created, version, revision) || (createdTagRevision ? createdTagRevision !== revision : created.target_commitish !== revision)) throw new Error('Created draft/tag no longer identifies the accepted source.');
  gh(['release', 'upload', tag, '--repo', repository, ...names.map(asset => path.join(directory, asset)), '--clobber']);
  const draft = releaseById(created.id, tag);
  const finalTagRevision = tagRevision(tag);
  if (!draft.draft || !matchingAssets(draft) || (finalTagRevision ? finalTagRevision !== revision : draft.target_commitish !== revision)) throw new Error('Uploaded draft assets/source do not match the independently accepted files.');
  gh(['release', 'edit', tag, '--repo', repository, '--draft=false', '--prerelease=false', '--latest=false']);
  if (tagRevision(tag) !== revision) throw new Error('Published release tag does not identify the accepted source.');
}

const publicUrl = `https://github.com/${repository}/releases/download/${tag}/${name}`;
let verified = false;
for (let attempt = 0; attempt < 4; attempt++) {
  const response = await fetch(publicUrl, { signal: AbortSignal.timeout(180000) });
  if (!response.ok) {
    await response.body?.cancel();
    if (![404, 429, 502, 503, 504].includes(response.status) || attempt === 3) throw new Error('Published installer could not be verified by public download: HTTP ' + response.status);
    await new Promise(resolve => setTimeout(resolve, 2000 * (attempt + 1))); continue;
  }
  const hash = createHash('sha256'); let size = 0;
  for await (const chunk of response.body) { size += chunk.length; if (size > metadata.installer.size) throw new Error('Published download exceeds the accepted installer size.'); hash.update(chunk); }
  if (size !== metadata.installer.size || hash.digest('hex') !== metadata.installer.sha256) throw new Error('Public installer bytes differ from the accepted EXE.');
  verified = true; break;
}
if (!verified) throw new Error('Public installer download was not verified.');
const url = `https://github.com/${repository}/releases/tag/${tag}`;
console.log(`Published and verified ${url}\nSHA256 ${metadata.installer.sha256}`);
if (process.env.GITHUB_STEP_SUMMARY) await fs.appendFile(process.env.GITHUB_STEP_SUMMARY, `Published [Neutron Launcher ${version}](${url}) from accepted source \`${revision}\`.\n\nPublic EXE SHA256: \`${metadata.installer.sha256}\`.\n`);
