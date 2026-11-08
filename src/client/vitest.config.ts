import { globSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { playwright } from '@vitest/browser-playwright'
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

const CLIENT_ROOT = fileURLToPath(new URL('.', import.meta.url))

// QA-10 : 90 % des lignes pour le noyau cryptographique, et des branches en plus (ADR 0012, point 4).
const PER_PATH_THRESHOLDS = {
  'src/shared/crypto/**': { lines: 90, branches: 90 },
}

// Un seuil dont le glob ne trouve aucun fichier ne vérifierait plus rien, par exemple après un déplacement :
// la configuration échoue plutôt que de laisser passer une couverture nulle (ADR 0012, point 4).
for (const glob of Object.keys(PER_PATH_THRESHOLDS)) {
  const files = globSync(glob, { cwd: CLIENT_ROOT, withFileTypes: true }).filter((entry) =>
    entry.isFile(),
  )
  if (files.length === 0) {
    throw new Error(`Seuil de couverture sans aucun fichier : ${glob}`)
  }
}

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      projects: [
        {
          extends: true,
          test: {
            name: 'node',
            // Node fournit WebCrypto (Ed25519, X25519, AES-GCM) : le noyau cryptographique s'y teste tel quel.
            // eslint.config.test.ts éprouve les règles de l'ADR 0012 sur le mini-projet eslint-fixtures/.
            include: ['src/**/*.test.ts', 'eslint.config.test.ts'],
            environment: 'node',
          },
        },
        {
          extends: true,
          test: {
            // ADR 0012, point 9 : les composants se testent dans un vrai Chromium, sans jsdom.
            name: 'browser',
            include: ['src/**/*.test.tsx'],
            browser: {
              enabled: true,
              headless: true,
              provider: playwright(),
              instances: [{ browser: 'chromium' }],
            },
          },
        },
      ],
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
        // QA-10 : 70 % des lignes au global.
        thresholds: {
          lines: 70,
          ...PER_PATH_THRESHOLDS,
        },
      },
    },
  }),
)
