import { expect, test } from 'vitest';
import { mapLimit } from '../electron/parallel.cts';

test('bounded work preserves order and waits for active readers before reporting failure', async () => {
  let active = 0; let peak = 0;
  const result = await mapLimit([3, 2, 1, 0], 2, async value => { active++; peak = Math.max(peak, active); await new Promise(done => setTimeout(done, value)); active--; return value * 2; });
  expect(result).toEqual([6, 4, 2, 0]); expect(peak).toBe(2);
  let siblingDone = false;
  await expect(mapLimit([0, 1, 2], 2, async value => { if (value === 0) throw new Error('bad digest'); await new Promise(done => setTimeout(done, 10)); siblingDone = true; })).rejects.toThrow('bad digest');
  expect(siblingDone).toBe(true); expect(active).toBe(0);
});
