import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import { createTypeScriptImportResolver } from 'eslint-import-resolver-typescript'
import boundaries from 'eslint-plugin-boundaries'
import { importX } from 'eslint-plugin-import-x'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

const CRYPTO_ONLY =
  'Seul src/shared/crypto/ appelle WebCrypto et HPKE (ADR 0012) : passez par son API publique.'

// ADR 0012 : couches de Feature-Sliced Design. Une tranche n'expose que son index.ts et se découpe en segments
// déclarés, plus @x pour les entités ; shared se découpe en segments déclarés. Tout autre fichier est refusé
// (boundaries/no-unknown-files) : un dossier non déclaré contournerait le sens ui → model → api.
const LAYER_FOLDERS = { page: 'pages', widget: 'widgets', feature: 'features', entity: 'entities' }
const SLICE_LAYERS = Object.keys(LAYER_FOLDERS)
const SLICE_SEGMENTS = ['ui', 'model', 'api', 'lib', 'config']
const SHARED_SEGMENTS = ['crypto', 'api', 'ws', 'i18n', 'ui', 'styles', 'lib', 'config']
const SEGMENTS = SLICE_LAYERS.map((layer) => `${layer}-segment`)
const segmentsOf = (layer) => (layer === 'entity' ? [...SLICE_SEGMENTS, '@x'] : SLICE_SEGMENTS)
// Tranches des couches strictement inférieures, atteintes par leur index.ts ; shared, par l'index.ts d'un segment.
const LOWER_SLICES = {
  app: ['pages', 'widgets', 'features', 'entities'],
  page: ['widgets', 'features', 'entities'],
  widget: ['features', 'entities'],
  feature: ['entities'],
  entity: [],
}
const publicApi = (folders) => ({
  file: { categories: 'public-api', captured: folders.map((layer) => ({ layer })) },
})
const sharedIndex = { element: { type: 'shared', fileInternalPath: 'index.ts' } }
const sharedSegment = (segment) => ({ element: { type: 'shared', captured: { segment } } })
const segment = (name) => ({ element: { type: SEGMENTS, captured: { segment: name } } })

export default defineConfig(
  // eslint-fixtures/ : mini-projet témoin, linté par eslint.config.test.ts seulement.
  globalIgnores(['dist', 'coverage', 'playwright-report', 'test-results', 'eslint-fixtures']),

  {
    files: ['**/*.js'],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },

  // QA-10 : analyse statique typée et stricte, aucun any.
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      importX.flatConfigs.recommended,
      importX.flatConfigs.typescript,
    ],
    languageOptions: {
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
    settings: {
      'import-x/resolver-next': [createTypeScriptImportResolver()],
    },
    rules: {
      'import-x/no-cycle': 'error',
      'import-x/no-duplicates': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },

  // Code du navigateur : règles React et accessibilité.
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [reactHooks.configs.flat['recommended-latest'], jsxA11y.flatConfigs.recommended],
    languageOptions: { globals: globals.browser },
  },

  // ADR 0012 : WebCrypto et HPKE ne sont accessibles que depuis src/shared/crypto/.
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/shared/crypto/**'],
    rules: {
      'no-restricted-globals': ['error', { name: 'crypto', message: CRYPTO_ONLY }],
      'no-restricted-properties': [
        'error',
        { object: 'window', property: 'crypto', message: CRYPTO_ONLY },
        { object: 'globalThis', property: 'crypto', message: CRYPTO_ONLY },
        { object: 'self', property: 'crypto', message: CRYPTO_ONLY },
      ],
      'no-restricted-imports': [
        'error',
        { patterns: [{ group: ['@hpke/*'], message: CRYPTO_ONLY }] },
      ],
    },
  },

  // ADR 0012 : frontières de Feature-Sliced Design et frontière entre cryptographie et réseau. Les politiques
  // s'appliquent dans l'ordre et la dernière qui correspond l'emporte : les interdictions viennent donc après les
  // permissions qu'elles restreignent. Chaque frontière est éprouvée par eslint.config.test.ts.
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      'import/resolver': { typescript: { alwaysTryTypes: true } },
      'boundaries/elements': [
        ...SLICE_LAYERS.map((layer) => ({
          type: `${layer}-segment`,
          pattern: `src/${LAYER_FOLDERS[layer]}/*/{${segmentsOf(layer).join(',')}}`,
          capture: ['slice', 'segment'],
          partialMatch: false,
        })),
        {
          type: 'shared',
          pattern: `src/shared/{${SHARED_SEGMENTS.join(',')}}`,
          capture: ['segment'],
          partialMatch: false,
        },
        { type: 'app', pattern: 'src/app', partialMatch: false },
      ],
      'boundaries/files': [
        { pattern: 'src/main.tsx', category: 'entry' },
        // Racine d'une tranche : son API publique, index.ts, et rien d'autre.
        {
          pattern: `src/{${Object.values(LAYER_FOLDERS).join(',')}}/*/index.ts`,
          category: 'public-api',
          capture: ['layer', 'slice'],
        },
      ],
    },
    rules: {
      // Tout fichier appartient à une couche, et aucun import ne vise un fichier hors des couches.
      'boundaries/no-unknown-files': 'error',
      'boundaries/no-unknown-dependencies': 'error',
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            // Fichiers d'un même élément.
            { allow: { dependency: { relationship: { to: 'internal' } } } },
            // Le point d'entrée monte app.
            {
              from: { file: { categories: 'entry' } },
              allow: { to: { element: { type: 'app' } } },
            },
            // Une tranche : ses segments se voient entre eux, et son index.ts les expose.
            ...SLICE_LAYERS.map((layer) => ({
              from: { element: { type: `${layer}-segment` } },
              allow: {
                to: {
                  element: {
                    type: `${layer}-segment`,
                    captured: { slice: '{{ from.element.captured.slice }}' },
                  },
                },
              },
            })),
            ...SLICE_LAYERS.map((layer) => ({
              from: publicApi([LAYER_FOLDERS[layer]]),
              allow: {
                to: {
                  element: {
                    type: `${layer}-segment`,
                    captured: { slice: '{{ from.file.captured.slice }}' },
                  },
                },
              },
            })),
            // Couches strictement inférieures : l'index.ts d'une tranche, ou celui d'un segment de shared.
            ...Object.entries(LOWER_SLICES).map(([layer, lowers]) => ({
              from: { element: { type: layer === 'app' ? 'app' : `${layer}-segment` } },
              allow: { to: lowers.length > 0 ? [publicApi(lowers), sharedIndex] : sharedIndex },
            })),
            // Segments de shared entre eux, par leur index.ts.
            { from: { element: { type: 'shared' } }, allow: { to: sharedIndex } },
            // Deux entités ne se parlent que par l'API @x dédiée.
            {
              from: { element: { type: 'entity-segment' } },
              allow: {
                to: {
                  element: {
                    type: 'entity-segment',
                    captured: { segment: '@x' },
                    fileInternalPath: '{{ from.element.captured.slice }}.ts',
                  },
                },
              },
            },
            // Clean architecture dans une tranche : ui → model → api.
            { from: segment('ui'), disallow: { to: segment('api') } },
            { from: segment('model'), disallow: { to: segment('ui') } },
            { from: segment('api'), disallow: { to: [segment('ui'), segment('model')] } },
            // lib et config sont des feuilles : sinon ui → lib → api sauterait model.
            {
              from: [segment('lib'), segment('config')],
              disallow: { to: [segment('ui'), segment('model'), segment('api')] },
            },
            // Réseau : seul un segment api/ atteint shared/api et shared/ws.
            {
              from: { element: { type: [...SEGMENTS, 'app'] } },
              disallow: { to: [sharedSegment('api'), sharedSegment('ws')] },
            },
            { from: sharedSegment('!api'), disallow: { to: sharedSegment('api') } },
            { from: sharedSegment('!ws'), disallow: { to: sharedSegment('ws') } },
            {
              from: segment('api'),
              allow: {
                to: [
                  {
                    element: {
                      type: 'shared',
                      captured: { segment: 'api' },
                      fileInternalPath: 'index.ts',
                    },
                  },
                  {
                    element: {
                      type: 'shared',
                      captured: { segment: 'ws' },
                      fileInternalPath: 'index.ts',
                    },
                  },
                ],
              },
            },
            // Cryptographie : shared/crypto ne s'atteint que depuis un segment model/ ou depuis app, et
            // n'importe aucun autre segment.
            {
              from: { element: { type: SEGMENTS } },
              disallow: { to: sharedSegment('crypto') },
            },
            { from: sharedSegment('!crypto'), disallow: { to: sharedSegment('crypto') } },
            {
              from: segment('model'),
              allow: {
                to: {
                  element: {
                    type: 'shared',
                    captured: { segment: 'crypto' },
                    fileInternalPath: 'index.ts',
                  },
                },
              },
            },
            { from: sharedSegment('crypto'), disallow: { to: sharedSegment('!crypto') } },
          ],
        },
      ],
    },
  },

  // Fichiers de configuration et tests de bout en bout : exécutés par Node.
  {
    files: ['*.config.ts', 'eslint.config.test.ts', 'e2e/**/*.ts'],
    languageOptions: { globals: globals.node },
  },

  prettier,
)
