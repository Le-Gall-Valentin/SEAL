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
  'Seul src/crypto/ appelle WebCrypto et HPKE (ADR 0001) : passez par son API publique, src/crypto/index.ts.'

export default defineConfig(
  globalIgnores(['dist', 'coverage', 'playwright-report', 'test-results']),

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

  // ADR 0001 : WebCrypto et HPKE ne sont accessibles que depuis src/crypto/.
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/crypto/**'],
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

  // ADR 0001 : frontières entre modules. crypto/ n'importe ni api/ ni ws/, et inversement.
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      'import/resolver': { typescript: { alwaysTryTypes: true } },
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app', partialMatch: false },
        { type: 'feature', pattern: 'src/features/*', capture: ['slice'], partialMatch: false },
        { type: 'crypto', pattern: 'src/crypto', partialMatch: false },
        { type: 'api', pattern: 'src/api', partialMatch: false },
        { type: 'ws', pattern: 'src/ws', partialMatch: false },
        { type: 'i18n', pattern: 'src/i18n', partialMatch: false },
        { type: 'shared', pattern: 'src/shared', partialMatch: false },
      ],
      'boundaries/files': [
        { pattern: 'src/main.tsx', category: 'entry' },
        { pattern: 'src/crypto/index.ts', category: 'crypto-api' },
      ],
    },
    rules: {
      // Tout fichier source appartient à un module de l'ADR 0001, sauf le point d'entrée.
      'boundaries/no-unknown-files': 'error',
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            // Bibliothèques et modules de Node : encadrés par no-restricted-imports ci-dessus.
            { allow: { to: { module: { origin: ['external', 'core'] } } } },
            // Fichiers d'un même module.
            { allow: { dependency: { relationship: { to: 'internal' } } } },
            {
              from: { file: { categories: 'entry' } },
              allow: { to: { element: { types: ['app', 'shared'] } } },
            },
            {
              from: { element: { type: 'app' } },
              allow: {
                to: { element: { types: ['feature', 'api', 'ws', 'i18n', 'shared'] } },
              },
            },
            {
              from: { element: { types: ['app', 'feature'] } },
              allow: { to: { element: { type: 'crypto' }, file: { categories: 'crypto-api' } } },
            },
            {
              from: { element: { type: 'feature' } },
              allow: { to: { element: { types: ['api', 'ws', 'i18n', 'shared'] } } },
            },
            {
              from: { element: { types: ['api', 'ws'] } },
              allow: { to: { element: { type: 'shared' } } },
            },
          ],
        },
      ],
    },
  },

  // Fichiers de configuration et tests de bout en bout : exécutés par Node.
  {
    files: ['*.config.ts', 'e2e/**/*.ts'],
    languageOptions: { globals: globals.node },
  },

  prettier,
)
