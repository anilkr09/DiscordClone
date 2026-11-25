import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from "path";
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  define: {
    global: 'window'
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  // Production bundles carry no console output, which could expose tokens or personal data.
  esbuild: mode === 'production' ? { drop: ['console', 'debugger'] as ('console' | 'debugger')[] } : {},
}))
