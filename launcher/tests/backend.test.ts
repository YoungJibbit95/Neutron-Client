import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { createHash, randomUUID } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import yazl from 'yazl';
import { archivePath, extractZip } from '../electron/archives.cts';
import { atomicJson, hash, owned } from '../electron/files.cts';
import { download, response } from '../electron/network.cts';
import { parseCommand } from '../electron/contracts.cts';
import { LauncherService } from '../electron/service.cts';
import { buildEnvironment, childEnvironment, runProcess } from '../electron/processes.cts';
import { launchArguments, verifyDistribution } from '../electron/distribution.cts';
import { Logs } from '../electron/logs.cts';
import { commandHash, inspectProcess, processMatches } from '../electron/lease.cts';
import { App } from '../src/App';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { initialState } from '../src/model';

let root: string;
beforeEach(async () => { root = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-backend-')); });
afterEach(async () => { vi.unstubAllGlobals(); await owned(root); await fs.rm(root, { recursive: true, force: true }); });
const digest = (value: string) => createHash('sha256').update(value).digest('hex');

describe('real file and process boundaries — no Electron or client start', () => {
  test('IPC permits only bounded typed domain commands, not executable arguments', () => {
    expect(parseCommand({ type: 'build', play: false })).toEqual({ type: 'build', play: false });
    expect(parseCommand({ type: 'save-options', patch: { gameDir: 'F:\\Spiel Prüfung', width: 1280 } }).type).toBe('save-options');
    for (const value of [{ type: 'shell', command: 'cmd' }, { type: 'build', play: 'yes' }, { type: 'stop', pid: 1 }, { type: 'remove', id: '../game' }, { type: 'save-preferences', patch: { scale: '100' } }, { type: 'save-options', patch: { ramMiB: NaN } }]) expect(() => parseCommand(value)).toThrow();
  });
  test('archive extraction rejects traversal, links and conflicting Windows paths', async () => {
    for (const name of ['../game', '/outside', 'C:/outside', 'a\\b', 'a/../b', 'a/CON.txt', 'a/x.']) expect(() => archivePath(name)).toThrow();
    const archive = path.join(root, 'conflict.zip'); const zip = new yazl.ZipFile();
    zip.addBuffer(Buffer.from('one'), 'folder/Ä space.txt'); zip.addBuffer(Buffer.from('two'), 'folder/ä space.txt');
    const complete = pipeline(zip.outputStream, createWriteStream(archive)); zip.end(); await complete;
    await expect(extractZip(archive, path.join(root, 'extracted'), new AbortController().signal)).rejects.toThrow('Duplicate');
    expect(await fs.readFile(path.join(root, 'extracted/folder/Ä space.txt'), 'utf8')).toBe('one');
    const linked = new yazl.ZipFile(); linked.addBuffer(Buffer.from('outside'), 'link', { mode: 0o120777 });
    const written = pipeline(linked.outputStream, createWriteStream(path.join(root, 'link.zip'))); linked.end(); await written;
    await expect(extractZip(path.join(root, 'link.zip'), path.join(root, 'linked'), new AbortController().signal)).rejects.toThrow('links');
  });
  test('owned paths reject root escape and junctions before writes', async () => {
    await expect(owned(root, '..', 'outside')).rejects.toThrow('leaves');
    const elsewhere = await fs.mkdtemp(path.join(os.tmpdir(), 'neutron-junction-'));
    try { await fs.symlink(elsewhere, path.join(root, 'link'), process.platform === 'win32' ? 'junction' : 'dir'); await expect(owned(root, 'link', 'state.json')).rejects.toThrow('Linked'); }
    finally { await fs.unlink(path.join(root, 'link')); await fs.rm(elsewhere, { recursive: true }); }
  });
  test('failed hash and interrupted download never replace the previous verified cache object', async () => {
    const target = path.join(root, 'download'); await fs.writeFile(target, 'old');
    vi.stubGlobal('fetch', vi.fn(async () => new Response('corrupt')));
    await expect(download({ url: 'https://repo.maven.apache.org/a', digest: digest('new') }, target, new AbortController().signal)).rejects.toThrow('mismatch');
    expect(await fs.readFile(target, 'utf8')).toBe('old');
    const cancelled = new AbortController(); cancelled.abort(new Error('cancelled fixture'));
    await expect(download({ url: 'https://repo.maven.apache.org/a', digest: digest('new') }, target, cancelled.signal)).rejects.toThrow('cancelled');
    expect(await fs.readFile(target, 'utf8')).toBe('old');
  });
  test('redirect targets are checked before following them', async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 302, headers: { location: 'http://127.0.0.1/private' } })); vi.stubGlobal('fetch', fetcher);
    await expect(response('https://api.github.com/repos/a/b', new AbortController().signal)).rejects.toThrow('host'); expect(fetcher).toHaveBeenCalledTimes(1);
  });
  test('settings persist across service restart and malformed settings are preserved for recovery', async () => {
    const first = new LauncherService(root, root); await first.initialize();
    await first.execute({ type: 'save-options', patch: { gameDir: path.join(root, 'Spiel Prüfung'), ramMiB: 2048 } });
    await first.execute({ type: 'save-preferences', patch: { locale: 'en', theme: 'ocean', scale: 125 } }); await first.shutdown();
    const second = new LauncherService(root, root); const restored = await second.initialize();
    expect(restored.options.ramMiB).toBe(2048); expect(restored.preferences).toMatchObject({ locale: 'en', theme: 'ocean', scale: 125 }); expect(restored.builds).toEqual([]); await second.shutdown();
    await fs.writeFile(path.join(root, 'state.json'), '{bad');
    const third = new LauncherService(root, root); expect((await third.initialize()).error).toContain('Saved state'); await third.shutdown();
    expect(await fs.readFile(path.join(root, 'state.json'), 'utf8')).toBe('{bad'); expect((await fs.readdir(root)).some(name => name.startsWith('state-recovery-'))).toBe(true);
  }, 15000);
  test('offline failure and cancellation preserve the installed selection and profile', async () => {
    const commit = 'a'.repeat(40); const id = commit + '-' + randomUUID();
    const service = new LauncherService(root, root); const defaults = await service.initialize(); await service.shutdown();
    await fs.mkdir(path.join(root, 'game')); await fs.writeFile(path.join(root, 'game/world.dat'), 'personal fixture');
    await atomicJson(path.join(root, 'state.json'), { schemaVersion: 1, options: defaults.options, preferences: defaults.preferences, builds: [{ id, commit, date: new Date().toISOString(), sizeMiB: 1, manifestSha256: 'b'.repeat(64) }], selected: id, runtime: null });
    const live = new LauncherService(root, root); await live.initialize();
    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('offline fixture'); }));
    await live.execute({ type: 'check' }); const failed = await live.idle(); expect(failed.online).toBe(false); expect(failed.selected).toBe(id);
    vi.stubGlobal('fetch', vi.fn((_url, options: RequestInit) => new Promise((_resolve, reject) => options.signal!.addEventListener('abort', () => reject(options.signal!.reason)))));
    await live.execute({ type: 'check' }); await live.execute({ type: 'cancel' }); const cancelled = await live.idle();
    expect(cancelled.status).toBe('cancelled'); expect(cancelled.selected).toBe(id); expect(await fs.readFile(path.join(root, 'game/world.dat'), 'utf8')).toBe('personal fixture'); await live.shutdown();
  });
  test('client manifest and file integrity reject mutation before a process can start', async () => {
    const commit = 'c'.repeat(40); const id = commit + '-' + randomUUID(); const folder = path.join(root, 'versions', id); await fs.mkdir(folder, { recursive: true });
    const jar = path.join(folder, 'neutron-client.jar'); await fs.writeFile(jar, 'fixture');
    const manifest = { schemaVersion: 1, entryPoint: 'me.neutron.launcher.ClientBootstrap', version: 'fixture', commit, classpath: [{ path: 'neutron-client.jar', sha256: await hash(jar) }] };
    await atomicJson(path.join(folder, 'launch-manifest.json'), manifest); const build = { id, commit, date: new Date().toISOString(), sizeMiB: 1, manifestSha256: await hash(path.join(folder, 'launch-manifest.json')) };
    expect((await verifyDistribution(root, build)).classpath).toEqual([jar]); await fs.writeFile(jar, 'tampered'); await expect(verifyDistribution(root, build)).rejects.toThrow('corrupt');
  });
  test('launch arguments preserve space/umlaut paths and reject memory or agent injection', () => {
    const options = { ...initialState().options, gameDir: path.join(root, 'Spiel Prüfung'), jvmArgs: '-XX:+UseG1GC' };
    const args = launchArguments(options, 4096, ['F:\\a b.jar', 'F:\\ü.jar'], root, 'Development', root);
    expect(args[args.indexOf('--gameDir') + 1]).toBe(options.gameDir); expect(args).toContain('-Xmx4096M');
    for (const jvmArgs of ['-Xmx8G', '-javaagent:evil.jar', 'MainClass', '-Djava.library.path=outside']) expect(() => launchArguments({ ...options, jvmArgs }, 4096, [], root, '', root)).toThrow();
  });
  test('real noninteractive child output and exit code are captured without inherited JVM injection', async () => {
    const lines: string[] = []; const env = childEnvironment(root, root); expect(env).not.toHaveProperty('JAVA_TOOL_OPTIONS');
    const code = await runProcess(process.execPath, ['-e', 'console.log("child fixture"); console.error("stderr fixture"); process.exitCode=7'], { cwd: root, env, line: line => lines.push(line) });
    expect(code).toBe(7); expect(lines).toContain('child fixture'); expect(lines).toContain('stderr fixture');
  });
  test('build heap bounds reach nested processes without leaking into game environments', async () => {
    vi.stubEnv('JAVA_TOOL_OPTIONS', '-javaagent:untrusted.jar'); vi.stubEnv('_JAVA_OPTIONS', '-Xmx99G');
    try {
      const env = buildEnvironment(root, root, root); const lines: string[] = [];
      const nested = 'console.log(JSON.stringify({heap:process.env.JAVA_TOOL_OPTIONS,legacy:process.env._JAVA_OPTIONS}))';
      const script = 'const r=require("node:child_process").spawnSync(process.execPath,["-e",' + JSON.stringify(nested) + '],{windowsHide:true,encoding:"utf8"});process.stdout.write(r.stdout);process.exitCode=r.status';
      expect(await runProcess(process.execPath, ['-e', script], { cwd: root, env, line: line => lines.push(line) })).toBe(0);
      expect(JSON.parse(lines[0])).toEqual({ heap: '-Xms32M -Xmx512M' });
      const game = childEnvironment(root, root); expect(game).not.toHaveProperty('JAVA_TOOL_OPTIONS'); expect(game).not.toHaveProperty('_JAVA_OPTIONS');
    } finally { vi.unstubAllEnvs(); }
  });
  test('rotating file logs export redacted retained output while UI buffer stays bounded', async () => {
    const logs = new Logs(root, () => {}); for (let index = 0; index < 2100; index++) logs.add('build', `row ${index} accessToken=secret-value C:\\Users\\Alice\\Game` + 'x'.repeat(1100));
    await logs.flush(); expect(logs.lines).toHaveLength(2000); expect(await fs.stat(path.join(root, 'logs/build.log.1'))).toBeTruthy();
    const text = await logs.export('build'); expect(text).toContain('row 0'); expect(text).toContain('row 2099'); expect(text).not.toContain('secret-value'); expect(text).not.toContain('Alice');
  });
  test.skipIf(process.platform !== 'win32')('cancellation terminates a real helper process and its child without shell invocation', async () => {
    const controller = new AbortController(); let childPid = 0;
    const script = 'const c=require("node:child_process").spawn(process.execPath,["-e","setInterval(()=>{},1000)"],{windowsHide:true,stdio:"ignore"});console.log(c.pid);setInterval(()=>{},1000);';
    await expect(runProcess(process.execPath, ['-e', script], { cwd: root, env: childEnvironment(root, root), signal: controller.signal, line: line => { childPid = Number(line); controller.abort(new Error('fixture cancel')); } })).rejects.toThrow('fixture cancel');
    expect(childPid).toBeGreaterThan(0);
    let alive = true;
    for (let attempt = 0; attempt < 20 && alive; attempt++) { try { process.kill(childPid, 0); await new Promise(resolve => setTimeout(resolve, 50)); } catch { alive = false; } }
    expect(alive).toBe(false);
  });
  test('default server output contains no mock controls, fixture commit, fake logs or simulated setup', () => {
    const html = renderToStaticMarkup(createElement(App));
    expect(html).not.toContain('prototype-controls'); expect(html).not.toContain('eeddb7d'); expect(html).not.toContain('Beispielkonsole'); expect(html).not.toContain('Einrichtung simulieren');
  });
  test('recovery checks executable, version path, command fingerprint and creation time before owning a PID', () => {
    const buildId = 'd'.repeat(40) + '-' + randomUUID();
    const identity = { pid: 123, executable: path.join(root, 'java.exe'), commandLine: `java -cp "${path.join(root, 'versions', buildId, 'client.jar')}" me.neutron.launcher.ClientBootstrap`, created: '2026-10-28T17:00:00Z' };
    const lease = { pid: identity.pid, executable: identity.executable, buildId, created: identity.created, commandHash: commandHash(identity) };
    expect(processMatches(root, lease, identity)).toBe(true);
    for (const changed of [{ ...identity, created: 'different' }, { ...identity, executable: path.join(root, 'another.exe') }, { ...identity, commandLine: 'unrelated process' }, { ...identity, pid: 124 }, null]) expect(processMatches(root, lease, changed)).toBe(false);
  });
  test.skipIf(process.platform !== 'win32')('Windows identity provider reads only this own noninteractive test process', async () => {
    const identity = await inspectProcess(process.pid, root);
    expect(identity?.pid).toBe(process.pid); expect(identity?.executable.toLowerCase()).toBe(process.execPath.toLowerCase()); expect(identity?.created).toBeTruthy();
  }, 15000);
});
