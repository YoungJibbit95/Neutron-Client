import { createHash } from 'node:crypto';
import yazl from 'yazl';
import type { ClientRelease } from '../electron/contracts.cts';

export const fixtureCommit = 'a'.repeat(40);
export const fixtureRuntime = { directory: 'runtimes/12345678-1234-1234-1234-123456789012', manifestSha256: 'b'.repeat(64) };
export const digest = (value: Buffer | string) => createHash('sha256').update(value).digest('hex');
const natives = [
  ['org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209', 'natives-windows', 'b84d5102b9dbfabfeb5e43c7e2828d98a7fc80e0', 613748],
  ['net.java.jinput:jinput-platform:2.0.5', 'natives-windows', '385ee093e01f587f30ee1c8a2ee7d408fd732e16', 155179],
  ['tv.twitch:twitch-platform:6.5', 'natives-windows-64', '9fdd0fd5aed0817063dcf95b69349a171f447ebd', 580098],
  ['tv.twitch:twitch-external-platform:4.5', 'natives-windows-64', 'c3cde57891b935d41b6680a9c5e1502eeab76d86', 7457619],
] as const;
export function versionJson(version = '1.2.3') {
  return { id: 'Neutron-' + version, assets: '1.8', mainClass: 'net.minecraft.client.main.Main', javaVersion: { majorVersion: 8 },
    assetIndex: { id: '1.8', sha1: 'f6ad102bcaa53b1a58358f16e376d548d44933ec', size: 78494, url: 'https://launchermeta.mojang.com/v1/packages/f6ad102bcaa53b1a58358f16e376d548d44933ec/1.8.json' },
    libraries: natives.map(([name, classifier, sha1, size]) => {
      const [group, artifact, release] = name.split(':'); const relative = `${group.replace(/\./g, '/')}/${artifact}/${release}/${artifact}-${release}-${classifier}.jar`;
      return { name, natives: { windows: classifier }, downloads: { classifiers: { [classifier]: { path: relative, url: 'https://libraries.minecraft.net/' + relative, sha1, size } } } };
    }) };
}
export async function jarFixture(omit?: string) {
  const zip = new yazl.ZipFile();
  const classes = ['me/neutron/launcher/ClientBootstrap.class', 'net/minecraft/client/main/Main.class', 'me/youngjibbit/Pulsar.class', 'org/lwjgl/opengl/Display.class', 'com/sun/jna/Native.class', 'oshi/SystemInfo.class', 'org/apache/logging/log4j/core/LoggerContext.class', 'com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class', 'org/reflections/Reflections.class'];
  for (const name of classes.filter(name => name !== omit)) zip.addBuffer(Buffer.from('test class bytes'), name);
  zip.addBuffer(Buffer.from('Manifest-Version: 1.0\r\nMain-Class: net.minecraft.client.main.Main\r\n\r\n'), 'META-INF/MANIFEST.MF');
  const result: Buffer[] = []; const complete = new Promise<Buffer>((resolve, reject) => { zip.outputStream.on('data', chunk => result.push(chunk)); zip.outputStream.on('error', reject); zip.outputStream.on('end', () => resolve(Buffer.concat(result))); });
  zip.end(); return complete;
}
export function releaseFixture(jar: Buffer = Buffer.from('jar fixture'), metadata: Buffer = Buffer.from(JSON.stringify(versionJson())), version = '1.2.3'): ClientRelease {
  const tag = 'v' + version; const makeAsset = (id: number, suffix: 'jar' | 'json', bytes: Buffer) => ({ id, name: `Neutron-${version}.${suffix}`, url: `https://github.com/YoungJibbit95/Neutron-Client/releases/download/${tag}/Neutron-${version}.${suffix}`, sha256: digest(bytes), size: bytes.length });
  return { id: 123, tag, version, publishedAt: '2026-11-09T12:00:00Z', prerelease: version.includes('-'), jar: makeAsset(124, 'jar', jar), json: makeAsset(125, 'json', metadata) };
}
export function githubRelease(release = releaseFixture()) {
  return { id: release.id, tag_name: release.tag, draft: false, prerelease: release.prerelease, published_at: release.publishedAt,
    assets: [release.jar, release.json].map(item => ({ id: item.id, name: item.name, state: 'uploaded', browser_download_url: item.url, digest: 'sha256:' + item.sha256, size: item.size })) };
}
