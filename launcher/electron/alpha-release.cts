export interface LauncherRelease {
  schemaVersion: 2;
  channel: 'alpha' | 'stable';
  launcherVersion: string;
  repository: 'YoungJibbit95/Neutron-Client';
}

// The trusted package selects the release repository. Renderer commands only
// select a tag returned by that repository's verified client release catalog.
export function parseLauncherRelease(value: unknown): LauncherRelease {
  const item = value as Partial<LauncherRelease> | null;
  if (!item || item.schemaVersion !== 2 || !['alpha', 'stable'].includes(item.channel ?? '') ||
      typeof item.launcherVersion !== 'string' || !(item.channel === 'stable' ? /^\d+\.\d+\.\d+$/ : /^\d+\.\d+\.\d+-alpha\.\d+$/).test(item.launcherVersion) ||
      item.repository !== 'YoungJibbit95/Neutron-Client' ||
      Object.keys(item).some(key => !['schemaVersion', 'channel', 'launcherVersion', 'repository'].includes(key))) {
    throw new Error('Invalid launcher release metadata. Reinstall a verified installer.');
  }
  return { schemaVersion: 2, channel: item.channel as LauncherRelease['channel'], launcherVersion: item.launcherVersion, repository: item.repository };
}
