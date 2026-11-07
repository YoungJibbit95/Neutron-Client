export interface AlphaRelease {
  schemaVersion: 1;
  channel: 'alpha';
  launcherVersion: string;
  clientCommit: string;
  sourceArchiveSha256: string;
  testedClientCommit?: string;
}

// Packaged input only. Neither the renderer nor a command-line argument can
// substitute a moving branch, source URL or executable for the reviewed pin.
export function parseAlphaRelease(value: unknown): AlphaRelease {
  const item = value as Partial<AlphaRelease> | null;
  if (!item || item.schemaVersion !== 1 || item.channel !== 'alpha' ||
      typeof item.launcherVersion !== 'string' || !/^\d+\.\d+\.\d+-alpha\.\d+$/.test(item.launcherVersion) ||
      typeof item.clientCommit !== 'string' || !/^[a-f0-9]{40}$/.test(item.clientCommit) ||
      typeof item.sourceArchiveSha256 !== 'string' || !/^[a-f0-9]{64}$/.test(item.sourceArchiveSha256) ||
      item.testedClientCommit !== undefined && item.testedClientCommit !== item.clientCommit) {
    throw new Error('Invalid pinned Alpha release. Reinstall a verified installer.');
  }
  return { schemaVersion: 1, channel: 'alpha', launcherVersion: item.launcherVersion,
    clientCommit: item.clientCommit, sourceArchiveSha256: item.sourceArchiveSha256,
    ...(item.testedClientCommit ? { testedClientCommit: item.testedClientCommit } : {}) };
}
