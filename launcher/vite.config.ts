import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: './',
  plugins: [react(), {
    name: 'local-development-csp', apply: 'serve',
    transformIndexHtml(html) { return html.replace("connect-src 'none'", "connect-src 'self' ws://127.0.0.1:*"); },
  }],
  oxc: { include: /\.[cm]?[jt]sx?$/ },
  cacheDir: process.env.NEUTRON_VITE_CACHE ?? 'node_modules/.vite',
  build: { target: 'es2022', outDir: 'dist', emptyOutDir: true },
  test: { environment: 'node', include: ['tests/**/*.test.{ts,tsx}'], maxWorkers: 2 },
});
