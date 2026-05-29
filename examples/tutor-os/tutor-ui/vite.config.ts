import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

/**
 * Vite config — minimal on purpose.
 *
 *   @ alias        →  src/
 *   dev proxy      →  /session, /progress, /plan, /quiz, /ws → tutor-api
 *                    (defaults to localhost:8080; override with VITE_API_BASE_URL)
 *
 * The proxy lets the frontend call `fetch('/session/start')` and
 * `new WebSocket('/ws/session/.../stream')` directly without CORS gymnastics
 * during development. In production, set VITE_API_BASE_URL to the deployed
 * API origin and the api client will hit it directly.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const raw = (env.VITE_API_BASE_URL || '').trim();
  // Only accept an absolute http(s) URL; anything else falls back to the
  // default. A relative value like "/" breaks http-proxy (null host → crash
  // in setupOutgoing) so we reject it here with a clear warning.
  const isAbsoluteHttp = /^https?:\/\/[^/]+/i.test(raw);
  if (raw && !isAbsoluteHttp) {
    console.warn(
      `[vite] VITE_API_BASE_URL=${JSON.stringify(raw)} is not an absolute http(s) URL — ` +
      `falling back to http://localhost:8080`
    );
  }
  const apiBase = isAbsoluteHttp ? raw : 'http://localhost:8080';
  const wsBase  = apiBase.replace(/^http/, 'ws');

  return {
    plugins: [react()],
    resolve: {
      alias: { '@': path.resolve(__dirname, './src') }
    },
    server: {
      port: 5173,
      host: true,
      proxy: {
        '/session':  { target: apiBase, changeOrigin: true },
        '/progress': { target: apiBase, changeOrigin: true },
        '/plan':     { target: apiBase, changeOrigin: true },
        '/quiz':     { target: apiBase, changeOrigin: true },
        '/ws':       { target: wsBase,  ws: true, changeOrigin: true }
      }
    }
  };
});
