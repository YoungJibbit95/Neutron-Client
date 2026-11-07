import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { hash } from './files.cjs';

const buildInputs = ['pom.xml', 'src/build/javafx8.xml', 'src/main/java/me/neutron/launcher/ClientBootstrap.java', 'src/test/java/me/neutron/launcher/ClientBootstrapTest.java'];
// Mojang's published artifacts are hashed by legacy-dependencies.cts. The client
// historically used aliases that do not have authoritative Maven descriptors.
const coordinates: [string, string | null][] = [
  ['com.mojang:patchy:1.7.7', null], // No source references and no official artifact.
  ['com.mojang:icu4j-core-mojang:51.2', 'com.ibm.icu:icu4j-core-mojang:51.2'],
  ...['libraryjavasound:20101123', 'codecwav:20101023', 'soundsystem:20120107', 'codecjorbis:20101023'].map(value => ['com.paulscode.sound:' + value, 'com.paulscode:' + value] as [string, string]),
  ['org.lwjgl:lwjgl:2.9.4-nightly', 'org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209'],
  ['org.lwjgl:util:2.9.4-nightly', 'org.lwjgl.lwjgl:lwjgl_util:2.9.4-nightly-20150209'],
];
export const normalizedSourceHash = (text: string) => createHash('sha256').update(text.replace(/\r\n/g, '\n').trimEnd() + '\n').digest('hex');

export async function adaptSource(source: string, adapter: string, signal: AbortSignal) {
  signal.throwIfAborted();
  const contract = JSON.parse(await fs.readFile(path.join(adapter, 'contract.json'), 'utf8')) as { schemaVersion: number; reviewedCommit: string; inputs: Record<string, string> };
  if (contract.schemaVersion !== 1 || !/^[a-f0-9]{40}$/.test(contract.reviewedCommit)) throw new Error('Invalid reviewed source contract.');
  const records = [];
  // Validate every build-critical input before writing anything. Bootstrap and
  // JavaFX are upstream sources, never injected or replaced by this adapter.
  for (const relative of buildInputs) {
    signal.throwIfAborted();
    const target = path.join(source, relative); const value = await fs.readFile(target, 'utf8');
    if (!/^[a-f0-9]{64}$/.test(contract.inputs[relative] ?? '') || normalizedSourceHash(value) !== contract.inputs[relative]) throw new Error('Upstream build input changed: ' + relative + '. A reviewed launcher adapter update is required.');
    records.push({ path: relative, sha256: await hash(target) });
  }
  const pom = path.join(source, 'pom.xml'); let result = await fs.readFile(pom, 'utf8');
  for (const [from, to] of coordinates) {
    let count = 0;
    result = result.replace(/<dependency>[\s\S]*?<\/dependency>/g, block => {
      const tags = ['groupId', 'artifactId', 'version'];
      if (tags.map(tag => block.match(new RegExp('<' + tag + '>([^<]+)</' + tag + '>'))?.[1]).join(':') !== from) return block;
      count++; if (!to) return '<!-- Unused patchy:1.7.7 has no official artifact; omitted by the reviewed launcher adapter. -->';
      return tags.reduce((value, tag, index) => value.replace(new RegExp('(<'+ tag + '>)[^<]+(</' + tag + '>)'), (_match, start: string, end: string) => start + to.split(':')[index] + end), block);
    });
    if (count !== 1) throw new Error('Reviewed legacy dependency contract changed: ' + from);
  }
  signal.throwIfAborted(); await fs.writeFile(pom, result);
  return { reviewedCommit: contract.reviewedCommit, files: records };
}
