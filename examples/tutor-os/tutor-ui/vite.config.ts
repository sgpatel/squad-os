import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// Vite config kept minimal on purpose — this is a reference example, not a SaaS.
// The @ alias keeps imports tidy: `import x from '@/lib/types'`.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  },
  server: { port: 5173, host: true }
});
