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

// ADR 0012, point 2 : chaque capacité sensible du navigateur n'est atteinte que depuis son segment de shared. Une
// globale est aussi refusée comme propriété de n'importe quel objet (window.fetch, globalThis.window.crypto).
const CAPABILITIES = {
  // WebCrypto, HPKE et le coffre des clés en IndexedDB (KEY-40, KEY-41).
  crypto: {
    folder: 'src/shared/crypto',
    message:
      'Seul src/shared/crypto/ appelle WebCrypto, HPKE et IndexedDB (ADR 0012, KEY-40) : passez par son API publique.',
    globals: ['crypto', 'indexedDB'],
    properties: [],
    // navigator.storage.persist() protège le coffre de l'éviction (ADR 0012, audit, Q6).
    qualifiedProperties: [{ object: 'navigator', property: 'storage' }],
    importPaths: ['crypto', 'node:crypto', 'idb'],
    importPatterns: ['@hpke/*', '@noble/*', 'idb/*'],
    // Mêmes cibles pour import(), que no-restricted-imports ne voit pas (esquery n'admet pas « / » dans un motif).
    dynamicImport: String.raw`/^(node:)?crypto$|^idb\b|^@(hpke|noble)\b/`,
  },
  // Toutes les voies HTTP du navigateur, et le cookie XSRF-TOKEN que le client HTTP renvoie en en-tête.
  http: {
    folder: 'src/shared/api',
    message:
      'Seul src/shared/api/ appelle le réseau HTTP (ADR 0012) : passez par le segment api/ de la tranche.',
    globals: ['fetch', 'XMLHttpRequest', 'EventSource', 'WebTransport'],
    properties: ['sendBeacon', 'cookie'],
    qualifiedProperties: [],
    importPaths: [],
    importPatterns: [],
    dynamicImport: null,
  },
  ws: {
    folder: 'src/shared/ws',
    message:
      'Seul src/shared/ws/ ouvre une WebSocket (ADR 0012) : passez par le segment api/ de la tranche.',
    globals: ['WebSocket'],
    properties: [],
    qualifiedProperties: [],
    importPaths: [],
    importPatterns: [],
    dynamicImport: null,
  },
}
const PEER_TO_PEER =
  'Aucun canal pair à pair (ADR 0012) : la CSP ne borne pas WebRTC, qui contournerait shared/api et shared/ws.'
const STORAGE =
  'Aucun stockage web persistant (KEY-41) : ni texte clair ni secret hors du coffre IndexedDB de shared/crypto.'
const RAW_HTML = 'Aucun rendu HTML brut (SEC-02, A05) : affichez du texte, React l’échappe.'
const CONSOLE =
  'Aucune écriture dans la console (SEC-21) : elle pourrait révéler un secret ou un texte clair.'
const GLOBAL_OBJECT =
  "L'objet global ne s'utilise que par un accès nommé, comme globalThis.addEventListener (ADR 0012) : un alias, un " +
  'accès calculé ou un passage en paramètre contourneraient les frontières de la cryptographie et du réseau.'
const COOKIE_STORE =
  'Aucun cookie par cookieStore (ADR 0012, KEY-41) : seul src/shared/api/ lit XSRF-TOKEN, par document.cookie.'
const NODE_MODULES =
  'Une bibliothèque s’importe par son nom, jamais par son chemin dans node_modules (ADR 0012) : sinon les frontières ne la voient pas.'
// Autres noms de la fenêtre : SEAL n'est jamais encadré, donc frames, top, parent et opener désignent l'objet global.
const WINDOW_NAMES = ['frames', 'top', 'parent', 'opener']

// ADR 0012, point 2 : puits interdits dans tout le code du navigateur, shared compris.
const SINK_GLOBALS = [
  ...['localStorage', 'sessionStorage', 'caches'].map((name) => ({ name, message: STORAGE })),
  { name: 'RTCPeerConnection', message: PEER_TO_PEER },
  { name: 'cookieStore', message: COOKIE_STORE },
  ...WINDOW_NAMES.map((name) => ({ name, message: GLOBAL_OBJECT })),
]
// Comme valeur, console ne sert qu'à l'espionner dans un test (vi.spyOn) : la règle des tests l'omet.
const CONSOLE_GLOBAL = { name: 'console', message: CONSOLE }
const SINK_PROPERTIES = [
  ...['localStorage', 'sessionStorage', 'caches'].map((property) => ({
    property,
    message: STORAGE,
  })),
  { property: 'getDirectory', message: STORAGE },
  { property: 'RTCPeerConnection', message: PEER_TO_PEER },
  { property: 'cookieStore', message: COOKIE_STORE },
  ...[
    'innerHTML',
    'outerHTML',
    'insertAdjacentHTML',
    'setHTMLUnsafe',
    'createContextualFragment',
    'parseFromString',
    'parseHTMLUnsafe',
    'srcdoc',
  ].map((property) => ({ property, message: RAW_HTML })),
  ...['write', 'writeln'].map((property) => ({ object: 'document', property, message: RAW_HTML })),
  { property: 'console', message: CONSOLE },
  ...['defaultView', 'window', 'self', 'globalThis', 'frames', 'contentWindow'].map((property) => ({
    property,
    message: GLOBAL_OBJECT,
  })),
  // Pas sur tout objet : rect.top et node.parent sont légitimes.
  ...['window', 'self', 'globalThis'].flatMap((object) =>
    WINDOW_NAMES.map((property) => ({ object, property, message: GLOBAL_OBJECT })),
  ),
]
const SINK_SYNTAX = [
  {
    selector: 'JSXAttribute[name.name=/^(dangerouslySetInnerHTML|srcDoc)$/]',
    message: RAW_HTML,
  },
  { selector: "Property[key.name='dangerouslySetInnerHTML']", message: RAW_HTML },
  {
    selector: "JSXAttribute[name.name='style']",
    message: 'Aucun style en ligne (TRN-21) : CSS Modules et jetons de la maquette.',
  },
  {
    selector:
      'Identifier[name=/^(globalThis|window|self)$/]:not(MemberExpression[computed=false] > Identifier.object, ' +
      'MemberExpression[computed=false] > Identifier.property, Property > Identifier.key)',
    message: GLOBAL_OBJECT,
  },
  {
    selector: "ImportExpression[source.type!='Literal']",
    message:
      'Un import dynamique nomme sa cible en clair (ADR 0012) : sinon les frontières ne la voient pas.',
  },
  { selector: 'ImportExpression[source.value=/node_modules/]', message: NODE_MODULES },
  {
    selector: "MemberExpression[object.type='MetaProperty'][property.name='glob']",
    message:
      'import.meta.glob est refusé (ADR 0012) : il chargerait des fichiers sans passer par les frontières.',
  },
  {
    selector:
      ':matches(ExportAllDeclaration, ExportNamedDeclaration)[source.value=/shared.crypto/]',
    message:
      'shared/crypto ne se réexporte pas (ADR 0012) : seul un segment model/ ou app l’atteint, par son index.ts.',
  },
]

const deniedTo = (allowed) =>
  Object.entries(CAPABILITIES)
    .filter(([name]) => name !== allowed)
    .map(([, capability]) => capability)

function restrictedGlobals(allowed, { inTests }) {
  return [
    'error',
    ...SINK_GLOBALS,
    ...(inTests ? [] : [CONSOLE_GLOBAL]),
    ...deniedTo(allowed).flatMap(({ globals, message }) =>
      globals.map((name) => ({ name, message })),
    ),
  ]
}

// Règles d'une zone : les puits, plus les capacités des autres segments. Un bloc par zone, car ESLint remplace les
// options d'une règle d'un bloc à l'autre au lieu de les fusionner.
function restrictionsExcept(allowed) {
  const denied = deniedTo(allowed)
  return {
    'no-restricted-globals': restrictedGlobals(allowed, { inTests: false }),
    // Sans objet : window.fetch, globalThis.crypto ou globalThis.window.crypto sont tous refusés.
    'no-restricted-properties': [
      'error',
      ...SINK_PROPERTIES,
      ...denied.flatMap(({ globals, properties, qualifiedProperties, message }) => [
        ...[...globals, ...properties].map((property) => ({ property, message })),
        ...qualifiedProperties.map((qualified) => ({ ...qualified, message })),
      ]),
    ],
    'no-restricted-imports': [
      'error',
      {
        paths: denied.flatMap(({ importPaths, message }) =>
          importPaths.map((name) => ({ name, message })),
        ),
        patterns: [
          { regex: '(^|/)node_modules/', message: NODE_MODULES },
          ...denied.flatMap(({ importPatterns, message }) =>
            importPatterns.map((pattern) => ({ group: [pattern], message })),
          ),
        ],
      },
    ],
    'no-restricted-syntax': [
      'error',
      ...SINK_SYNTAX,
      ...denied.flatMap(({ dynamicImport, message }) =>
        dynamicImport
          ? [{ selector: `ImportExpression[source.value=${dynamicImport}]`, message }]
          : [],
      ),
    ],
  }
}

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

  // QA-10 (ADR 0012, audit) : sous src/, seulement du TypeScript ; un .mts ou un .js échapperait aux règles ci-dessous.
  {
    files: ['src/**/*.{js,jsx,mjs,cjs,mts,cts}'],
    languageOptions: { parser: tseslint.parser },
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: 'Program',
          message:
            'Sous src/, seuls les fichiers .ts et .tsx sont admis (ADR 0012) : les autres échapperaient aux frontières.',
        },
      ],
    },
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

  // ADR 0012, points 2 et 3 : puits interdits, rigueur, et capacités du navigateur réservées à leur segment.
  {
    files: ['src/**/*.{ts,tsx}'],
    // Aucune directive eslint-disable en ligne ne lève une frontière ou un puits (audit).
    linterOptions: { noInlineConfig: true },
    rules: {
      'no-console': 'error',
      'no-eval': 'error',
      'no-new-func': 'error',
      'no-script-url': 'error',
      'import-x/no-relative-packages': 'error',
      '@typescript-eslint/switch-exhaustiveness-check': 'error',
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: Object.values(CAPABILITIES).map(({ folder }) => `${folder}/**`),
    rules: restrictionsExcept(null),
  },
  ...Object.entries(CAPABILITIES).map(([name, { folder }]) => ({
    files: [`${folder}/**/*.{ts,tsx}`],
    rules: restrictionsExcept(name),
  })),
  {
    files: ['src/**/*.test.{ts,tsx}'],
    ignores: Object.values(CAPABILITIES).map(({ folder }) => `${folder}/**`),
    rules: { 'no-restricted-globals': restrictedGlobals(null, { inTests: true }) },
  },
  ...Object.entries(CAPABILITIES).map(([name, { folder }]) => ({
    files: [`${folder}/**/*.test.{ts,tsx}`],
    rules: { 'no-restricted-globals': restrictedGlobals(name, { inTests: true }) },
  })),

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
              allow: { to: { element: { type: 'app', fileInternalPath: 'index.ts' } } },
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
            // Une entité n'importe pas son propre @x, qui servirait de relais vers api/ (audit).
            {
              from: { element: { type: 'entity-segment' } },
              disallow: {
                to: {
                  element: {
                    type: 'entity-segment',
                    captured: { slice: '{{ from.element.captured.slice }}', segment: '@x' },
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
