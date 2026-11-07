import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      include: ['src/**/*.test.{ts,tsx}'],
      // Node fournit WebCrypto (Ed25519, X25519, AES-GCM) : le noyau cryptographique s'y teste tel quel.
      environment: 'node',
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,tsx}'],
        exclude: [
          'src/**/*.test.{ts,tsx}',
          'src/**/*.d.ts',
          // Racine de composition : du câblage seul (montage React, routeur du navigateur), couvert
          // par le test Playwright plutôt que par un test unitaire qui ne vérifierait que le montage.
          'src/main.tsx',
          'src/app/App.tsx',
        ],
        reporter: ['text', 'json-summary'],
        // QA-10 : 70 % des lignes au global, 90 % pour le noyau cryptographique.
        thresholds: {
          lines: 70,
          'src/crypto/**': { lines: 90 },
        },
      },
    },
  }),
)
