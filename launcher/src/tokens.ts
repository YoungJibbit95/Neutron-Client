import type { Theme } from './model';

export const themes = {
  sunset: { background: '#171719', sidebar: '#1c1a1d', panel: '#232024', raised: '#2c252b', text: '#f6f1f3', muted: '#c0b5bd', accent: '#ffb183', accentEnd: '#ff85b5', onAccent: '#28161e', border: '#443b43', controlBorder: '#796d78', positive: '#90e3b7', warning: '#f2ce8b', danger: '#ffa2aa' },
  ocean: { background: '#14191d', sidebar: '#182027', panel: '#1c272f', raised: '#26333e', text: '#f0f6fa', muted: '#b1c5d3', accent: '#8cddfa', accentEnd: '#9bbcff', onAccent: '#12232d', border: '#354955', controlBorder: '#6c8294', positive: '#92e0bf', warning: '#eed19a', danger: '#ffa6ac' },
  violet: { background: '#19171e', sidebar: '#1e1a26', panel: '#25212e', raised: '#30283b', text: '#f7f1fc', muted: '#c4b6d3', accent: '#c8aaff', accentEnd: '#f0a6d6', onAccent: '#24172f', border: '#45384f', controlBorder: '#82718f', positive: '#a0e3c0', warning: '#efd29b', danger: '#ffa7b6' },
  graphite: { background: '#18181a', sidebar: '#1d1d20', panel: '#252527', raised: '#303032', text: '#f5f3f0', muted: '#c1beb9', accent: '#e2d9cd', accentEnd: '#c3c2ce', onAccent: '#242326', border: '#444447', controlBorder: '#7b787d', positive: '#a6dfbf', warning: '#e8d3a9', danger: '#f5adb4' },
} satisfies Record<Theme, Record<string, string>>;

const cssNames: Record<keyof typeof themes.sunset, string> = {
  background: '--background', sidebar: '--sidebar', panel: '--panel', raised: '--raised',
  text: '--text', muted: '--muted', accent: '--accent', accentEnd: '--accent-end',
  onAccent: '--on-accent', border: '--border', controlBorder: '--control-border',
  positive: '--positive', warning: '--warning', danger: '--danger',
};
export function themeStyle(theme: Theme): Record<string, string> {
  return Object.fromEntries(Object.entries(themes[theme]).map(([key, value]) => [cssNames[key as keyof typeof cssNames], value]));
}
