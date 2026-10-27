import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const envDir = fileURLToPath(new URL('..', import.meta.url));
  const env = loadEnv(mode, envDir, '');
  // Server-side configuration only; client code always uses relative paths.
  const target = process.env.BACKEND_PROXY_TARGET
    ?? env.BACKEND_PROXY_TARGET
    ?? 'http://127.0.0.1:8080';

  return {
    envDir,
    plugins: [react(), tailwindcss()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target },
        '/ws': { target, ws: true },
      },
    },
  };
});
