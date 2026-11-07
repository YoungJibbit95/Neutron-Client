import { afterEach, beforeEach, expect, test } from 'vitest';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import yazl from 'yazl';
import { prepareSource } from '../electron/distribution.cts';
import { atomicJson, hash } from '../electron/files.cts';

const project = fileURLToPath(new URL('../../', import.meta.url));
const adapter = path.join(project, 'launcher/resources/source-adapter');
const inputs = ['pom.xml', 'src/build/javafx8.xml', 'src/main/java/me/neutron/launcher/ClientBootstrap.java', 'src/test/java/me/neutron/launcher/ClientBootstrapTest.java'];
const commit = 'a'.repeat(40);
let root: string;
beforeEach(async () => { root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-source-')); });
afterEach(async () => { await fs.rm(root, { recursive: true, force: true }); });

async function cachedSource(change?: string) {
  const archive = path.join(root, 'cache', 'source-' + commit + '.zip');
  await fs.mkdir(path.dirname(archive), { recursive: true });
  const zip = new yazl.ZipFile();
  for (const name of inputs) {
    const original = await fs.readFile(path.join(project, name));
    zip.addBuffer(change === name ? Buffer.concat([original, Buffer.from('\n<!-- unreviewed -->\n')]) : original, 'Neutron-Client-' + commit + '/' + name);
  }
  zip.addBuffer(Buffer.from('private fixture'), 'Neutron-Client-' + commit + '/test_run/saves/world.dat');
  const written = pipeline(zip.outputStream, createWriteStream(archive)); zip.end(); await written;
  await atomicJson(archive + '.json', { commit, sha256: await hash(archive) });
}

test('fresh source preparation preserves reviewed dependencies and JavaFX while adapting only legacy coordinates', async () => {
  await cachedSource();
  const prepared = await prepareSource(root, commit, adapter, new AbortController().signal);
  const upstream = await fs.readFile(path.join(project, 'pom.xml'), 'utf8');
  const pom = await fs.readFile(path.join(prepared.source, 'pom.xml'), 'utf8');
  for (const artifact of ['netty-all', 'jna', 'commons-io', 'log4j-core', 'lombok']) {
    const dependency = new RegExp('<dependency>\\s*<(?:groupId|artifactId)>[\\s\\S]*?</dependency>', 'g');
    const original = [...upstream.matchAll(dependency)].find(match => match[0].includes('<artifactId>' + artifact + '</artifactId>'))?.[0];
    expect(original, artifact).toBeTruthy(); expect(pom).toContain(original!);
  }
  expect(pom).toContain('<id>launcher-dist</id>'); expect(pom).toContain('src/build/javafx8.xml');
  expect(pom).toContain('<groupId>com.ibm.icu</groupId>'); expect(pom).toContain('<artifactId>lwjgl_util</artifactId>');
  expect(pom).not.toContain('<artifactId>patchy</artifactId>');
  for (const name of inputs.slice(1)) expect(await fs.readFile(path.join(prepared.source, name))).toEqual(await fs.readFile(path.join(project, name)));
  await expect(fs.access(path.join(prepared.source, 'test_run'))).rejects.toThrow();
  const snapshot = JSON.parse(await fs.readFile(path.join(prepared.work, 'source-snapshot.json'), 'utf8'));
  expect(snapshot.commit).toBe(commit); expect(snapshot.upstream.files).toHaveLength(inputs.length);
  expect(snapshot.files.find((item: {path: string}) => item.path === 'pom.xml').sha256).toBe(await hash(path.join(prepared.source, 'pom.xml')));
});

test.each(inputs)('unreviewed build input %s fails before the upstream POM is changed', async changed => {
  await cachedSource(changed);
  await expect(prepareSource(root, commit, adapter, new AbortController().signal)).rejects.toThrow(/build input changed/);
  const work = (await fs.readdir(path.join(root, 'work')))[0];
  const actual = await fs.readFile(path.join(root, 'work', work, 'source/pom.xml'), 'utf8');
  const original = await fs.readFile(path.join(project, 'pom.xml'), 'utf8');
  expect(actual).toBe(changed === 'pom.xml' ? original + '\n<!-- unreviewed -->\n' : original);
});

test('cancellation prevents source preparation from accepting a cached archive', async () => {
  await cachedSource(); const controller = new AbortController(); controller.abort(new Error('stopped fixture'));
  await expect(prepareSource(root, commit, adapter, controller.signal)).rejects.toThrow('stopped fixture');
});
