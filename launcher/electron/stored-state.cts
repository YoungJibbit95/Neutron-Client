import { optionPatch, preferencePatch, type BuildRecord, type Snapshot } from './contracts.cjs';
import type { Runtime } from './minecraft.cjs';

export interface StoredState { schemaVersion: 1; options: Snapshot['options']; preferences: Snapshot['preferences']; builds: BuildRecord[]; selected: string | null; runtime: Runtime | null }
export function parseStoredState(value: unknown): StoredState {
  const data = value as StoredState;
  if (!data || data.schemaVersion !== 1 || !Array.isArray(data.builds) || data.builds.length > 100 || data.builds.some(build => !build || !/^[a-f0-9]{40}-[a-f0-9-]{36}$/.test(build.id) || !/^[a-f0-9]{40}$/.test(build.commit) || !/^[a-f0-9]{64}$/.test(build.manifestSha256) || !Number.isFinite(build.sizeMiB) || build.sizeMiB < 0 || !Number.isFinite(Date.parse(build.date)))) throw new Error('Invalid persisted build registry.');
  const options = optionPatch(data.options); const preferences = preferencePatch(data.preferences);
  if (Object.keys(options).length !== 8 || Object.keys(preferences).length !== 5) throw new Error('Incomplete saved settings.');
  if (data.selected !== null && !data.builds.some(build => build.id === data.selected)) throw new Error('Invalid selected build registry.');
  if (data.runtime !== null && (!data.runtime || !/^runtimes\/[a-f0-9-]{36}$/.test(data.runtime.directory) || !/^[a-f0-9]{64}$/.test(data.runtime.manifestSha256))) throw new Error('Invalid runtime registry.');
  return { ...data, options: options as StoredState['options'], preferences: preferences as StoredState['preferences'] };
}
