import { fileURLToPath } from 'node:url'
import { ESLint, type Linter } from 'eslint'
import { createTypeScriptImportResolver } from 'eslint-import-resolver-typescript'
import { configs as tseslintConfigs } from 'typescript-eslint'
import { describe, expect, it } from 'vitest'

// La configuration du dépôt, appliquée au mini-projet témoin eslint-fixtures/ : chaque frontière et chaque règle de
// l'ADR 0012 doit refuser son cas interdit et accepter son cas permis (comme les règles ArchUnit du serveur).
const FIXTURES = fileURLToPath(new URL('./eslint-fixtures', import.meta.url))
const FIXTURES_TSCONFIG = `${FIXTURES}/tsconfig.json`

function fixtureLinter(typing: Linter.Config[]): ESLint {
  return new ESLint({
    cwd: FIXTURES,
    overrideConfigFile: fileURLToPath(new URL('./eslint.config.js', import.meta.url)),
    overrideConfig: [
      { ignores: ['!**/eslint-fixtures/**'] },
      ...typing,
      {
        settings: {
          'boundaries/root-path': FIXTURES,
          'import/resolver': { typescript: { alwaysTryTypes: true, project: FIXTURES_TSCONFIG } },
          'import-x/resolver-next': [
            createTypeScriptImportResolver({ project: FIXTURES_TSCONFIG }),
          ],
        },
      },
    ],
  })
}

// Typage coupé : rapide, et les extraits n'ont pas besoin d'exister sur le disque.
const eslint = fixtureLinter([
  tseslintConfigs.disableTypeChecked,
  { languageOptions: { parserOptions: { projectService: false, project: null } } },
])

// Typage actif, pour les règles qui en dépendent ; les extraits vivent dans src/shared/lib/, hors du disque.
const typedEslint = fixtureLinter([
  {
    languageOptions: {
      parserOptions: {
        projectService: {
          allowDefaultProject: ['src/shared/lib/*.ts'],
          defaultProject: 'tsconfig.json',
        },
        tsconfigRootDir: FIXTURES,
      },
    },
  },
])

async function ruleIds(file: string, code: string, linter = eslint): Promise<string[]> {
  const [result] = await linter.lintText(code, { filePath: `${FIXTURES}/${file}` })
  return (result?.messages ?? []).flatMap(
    (message) => message.ruleId ?? `analyse : ${message.message}`,
  )
}

async function boundaryRules(file: string, code: string): Promise<string[]> {
  return (await ruleIds(file, code)).filter((ruleId) => ruleId.startsWith('boundaries/'))
}

const IMPORT = (source: string) => `import { value } from '${source}'\nexport const used = value\n`

// Boundaries ignore un import qu'il ne résout pas : la cible doit exister, sinon le verdict ne prouverait rien.
async function importVerdict(file: string, source: string): Promise<string[]> {
  const rules = await ruleIds(file, IMPORT(source))
  expect(rules, `import non résolu : ${source}`).not.toContain('import-x/no-unresolved')
  return rules.filter((ruleId) => ruleId.startsWith('boundaries/'))
}

async function expectRefused(file: string, source: string) {
  expect(await importVerdict(file, source)).not.toEqual([])
}

async function expectAllowed(file: string, source: string) {
  expect(await importVerdict(file, source)).toEqual([])
}

describe('ADR 0012 : couches de Feature-Sliced Design', () => {
  it("QA-10 : une couche n'importe que des couches inférieures", async () => {
    await expectRefused('src/shared/ui/Button.ts', '@/features/sign-in')
    await expectRefused('src/entities/user/model/user.ts', '@/features/sign-in')
    await expectAllowed('src/pages/home/ui/HomePage.ts', '@/features/sign-in')
    await expectAllowed('src/app/router/routes.ts', '@/pages/home')
  })

  it("QA-10 : deux tranches d'une même couche ne s'importent pas", async () => {
    await expectRefused('src/features/sign-in/model/useSignIn.ts', '@/features/invite')
    await expectRefused('src/pages/home/ui/HomePage.ts', '@/pages/about')
  })

  it("QA-10 : on n'entre dans une tranche que par son index.ts", async () => {
    await expectRefused('src/pages/home/ui/HomePage.ts', '@/features/sign-in/ui/Form')
    await expectRefused('src/app/router/routes.ts', '@/shared/crypto/canonical')
    await expectAllowed('src/pages/home/ui/HomePage.ts', '@/features/sign-in')
  })

  it("QA-10 : l'index.ts d'une tranche n'expose que ses propres segments", async () => {
    await expectAllowed('src/features/sign-in/index.ts', './ui/Form')
    await expectAllowed('src/features/sign-in/index.ts', './model/useSignIn')
    await expectRefused('src/features/sign-in/index.ts', '@/features/invite')
    await expectRefused('src/features/sign-in/index.ts', '@/entities/user')
  })

  it('QA-10 : deux entités ne se parlent que par leur API @x', async () => {
    await expectRefused('src/entities/conversation/model/conversation.ts', '@/entities/user')
    await expectAllowed(
      'src/entities/conversation/model/conversation.ts',
      '@/entities/user/@x/conversation',
    )
  })

  it('QA-10 : dans une tranche, ui → model → api, jamais à rebours ni en sautant model', async () => {
    await expectRefused('src/features/sign-in/ui/Form.ts', '../api/signInRequest')
    await expectRefused('src/features/sign-in/model/useSignIn.ts', '../ui/Form')
    await expectRefused('src/features/sign-in/api/signInRequest.ts', '../model/useSignIn')
    await expectAllowed('src/features/sign-in/ui/Form.ts', '../model/useSignIn')
    await expectAllowed('src/features/sign-in/model/useSignIn.ts', '../api/signInRequest')
  })

  it('QA-10 : lib et config sont des feuilles : ui → lib → api ne contourne pas model', async () => {
    await expectRefused('src/features/sign-in/lib/format.ts', '../api/signInRequest')
    await expectRefused('src/features/sign-in/lib/format.ts', '../model/useSignIn')
    await expectRefused('src/features/sign-in/lib/format.ts', '../ui/Form')
    await expectRefused('src/features/sign-in/config/limits.ts', '../api/signInRequest')
    await expectAllowed('src/features/sign-in/ui/Form.ts', '../lib/format')
    await expectAllowed('src/features/sign-in/model/useSignIn.ts', '../lib/format')
    await expectAllowed('src/features/sign-in/api/signInRequest.ts', '../config/limits')
    await expectAllowed('src/features/sign-in/lib/format.ts', '../config/limits')
  })

  it('QA-10 : un fichier hors des couches, ou un import vers lui, est refusé', async () => {
    expect(await boundaryRules('src/misc/other.ts', 'export const other = 1\n')).toContain(
      'boundaries/no-unknown-files',
    )
    await expectRefused('src/features/sign-in/model/useSignIn.ts', '../../../misc/stray')
  })

  // Un dossier non déclaré serait rattaché à la racine de la tranche, qui voit tous ses segments : ui → foo → api
  // contournerait alors le sens ui → model → api.
  it.each([
    'src/features/sign-in/helpers.ts',
    'src/features/sign-in/store/state.ts',
    'src/features/sign-in/@x/user.ts',
    'src/shared/utils/format.ts',
  ])(
    "QA-10 : un fichier hors de l'index.ts et des segments déclarés est refusé (%s)",
    async (file) => {
      expect(await boundaryRules(file, 'export const other = 1\n')).toContain(
        'boundaries/no-unknown-files',
      )
    },
  )

  it.each([
    'src/features/sign-in/lib/format.ts',
    'src/features/sign-in/config/limits.ts',
    'src/entities/user/@x/conversation.ts',
    'src/shared/lib/format.ts',
    'src/shared/config/limits.ts',
  ])("QA-10 : un segment déclaré par l'ADR 0012 est reconnu (%s)", async (file) => {
    expect(await boundaryRules(file, 'export const other = 1\n')).toEqual([])
  })
})

describe('ADR 0012 : frontière entre cryptographie et réseau', () => {
  it('QA-10 : seul un segment api/ atteint le client HTTP et la WebSocket', async () => {
    await expectRefused('src/features/sign-in/ui/Form.ts', '@/shared/api')
    await expectRefused('src/features/sign-in/model/useSignIn.ts', '@/shared/ws')
    await expectAllowed('src/features/sign-in/api/signInRequest.ts', '@/shared/api')
    await expectAllowed('src/features/sign-in/api/signInRequest.ts', '@/shared/ws')
  })

  it("QA-10 : shared/crypto ne s'atteint que depuis un segment model/ ou depuis app", async () => {
    await expectRefused('src/features/sign-in/ui/Form.ts', '@/shared/crypto')
    await expectRefused('src/features/sign-in/api/signInRequest.ts', '@/shared/crypto')
    await expectAllowed('src/features/sign-in/model/useSignIn.ts', '@/shared/crypto')
    await expectAllowed('src/app/providers/Providers.ts', '@/shared/crypto')
  })

  it("QA-10 : shared/crypto n'importe aucun autre segment, ni le réseau n'importe shared/crypto", async () => {
    await expectRefused('src/shared/crypto/canonical.ts', '@/shared/ui')
    await expectRefused('src/shared/api/http.ts', '@/shared/crypto')
    await expectRefused('src/shared/ws/socket.ts', '@/shared/crypto')
    await expectAllowed('src/shared/ui/Button.ts', '@/shared/i18n')
  })
})

// ADR 0012, point 2 : règles qui interdisent un appel, un accès ou un import.
const SECURITY_RULES = new Set([
  'no-restricted-globals',
  'no-restricted-properties',
  'no-restricted-imports',
  'no-restricted-syntax',
  'no-console',
  'no-eval',
  'no-new-func',
  'no-script-url',
])

async function securityRules(file: string, code: string): Promise<string[]> {
  const rules = await ruleIds(file, code)
  expect(rules.filter((ruleId) => ruleId.startsWith('analyse'))).toEqual([])
  return rules.filter((ruleId) => SECURITY_RULES.has(ruleId))
}

async function expectForbidden(file: string, code: string) {
  expect(await securityRules(file, code), code).not.toEqual([])
}

async function expectPermitted(file: string, code: string) {
  expect(await securityRules(file, code), code).toEqual([])
}

const FEATURE_MODEL = 'src/features/sign-in/model/useSignIn.ts'
const FEATURE_API = 'src/features/sign-in/api/signInRequest.ts'
const SHARED_API = 'src/shared/api/http.ts'
const SHARED_WS = 'src/shared/ws/socket.ts'
const SHARED_CRYPTO = 'src/shared/crypto/keys.ts'
const SHARED_UI = 'src/shared/ui/Box.tsx'

describe('ADR 0012, point 2 : capacités du navigateur réservées à un segment de shared', () => {
  it('QA-10 : seul shared/api appelle fetch, XMLHttpRequest et EventSource', async () => {
    for (const file of [FEATURE_API, FEATURE_MODEL, SHARED_WS, SHARED_CRYPTO]) {
      await expectForbidden(file, "export const r = fetch('/api/v1/csrf')\n")
      await expectForbidden(file, "export const r = window.fetch('/api/v1/csrf')\n")
      await expectForbidden(file, 'export const r = new XMLHttpRequest()\n')
      await expectForbidden(file, "export const r = new EventSource('/api/v1/events')\n")
    }
    await expectPermitted(SHARED_API, "export const r = fetch('/api/v1/csrf')\n")
    await expectPermitted(SHARED_API, 'export const r = new XMLHttpRequest()\n')
    await expectPermitted(SHARED_API, "export const r = new EventSource('/api/v1/events')\n")
  })

  it('QA-10 : les autres voies HTTP et le cookie XSRF-TOKEN restent dans shared/api', async () => {
    for (const code of [
      "export const r = navigator.sendBeacon('/api/v1/x', 'd')\n",
      "export const r = new WebTransport('https://localhost/wt')\n",
      'export const r = document.cookie\n',
      'export const r = window.document.cookie\n',
    ]) {
      await expectForbidden(FEATURE_API, code)
      await expectForbidden(SHARED_CRYPTO, code)
      await expectPermitted(SHARED_API, code)
    }
  })

  it('QA-10 : aucun canal pair à pair, que la CSP ne borne pas', async () => {
    for (const file of [FEATURE_API, SHARED_API, SHARED_WS, SHARED_CRYPTO]) {
      await expectForbidden(file, 'export const p = new RTCPeerConnection()\n')
      await expectForbidden(file, 'export const p = new window.RTCPeerConnection()\n')
    }
  })

  it('QA-10 : seul shared/ws ouvre une WebSocket', async () => {
    for (const file of [FEATURE_API, SHARED_API, SHARED_CRYPTO]) {
      await expectForbidden(file, "export const s = new WebSocket('wss://localhost/ws')\n")
      await expectForbidden(
        file,
        "export const s = new globalThis.WebSocket('wss://localhost/ws')\n",
      )
    }
    await expectPermitted(SHARED_WS, "export const s = new WebSocket('wss://localhost/ws')\n")
  })

  it('QA-10 : seul shared/crypto atteint WebCrypto, quel que soit le chemin', async () => {
    for (const file of [FEATURE_MODEL, SHARED_API, SHARED_WS]) {
      await expectForbidden(file, 'export const c = crypto.subtle\n')
      await expectForbidden(file, 'export const c = globalThis.crypto.subtle\n')
      await expectForbidden(file, "export const c = Reflect.get(globalThis, 'crypto')\n")
      await expectForbidden(file, "const name = 'crypto'\nexport const c = globalThis[name]\n")
      await expectForbidden(file, 'const { crypto: c } = globalThis\nexport const k = c\n')
    }
    await expectPermitted(SHARED_CRYPTO, 'export const c = crypto.subtle\n')
    await expectPermitted(SHARED_CRYPTO, 'export const c = globalThis.crypto.getRandomValues\n')
  })

  it("QA-10 : l'objet global ne s'utilise que par un accès nommé, partout", async () => {
    for (const file of [FEATURE_MODEL, SHARED_CRYPTO]) {
      for (const code of [
        "const g = globalThis\nconst n = 'crypto'\nexport const r = g[n]\n",
        "export const r = Object.getOwnPropertyDescriptor(globalThis, 'crypto')\n",
        'export const r = Object.entries(window)\n',
        "const n = 'crypto'\nexport const r = document.defaultView?.[n]\n",
        "export const r = window['fe' + 'tch']\n",
      ]) {
        await expectForbidden(file, code)
      }
    }
    await expectPermitted(FEATURE_MODEL, "window.addEventListener('online', () => undefined)\n")
    await expectPermitted(FEATURE_MODEL, 'export const o = { window: 1 }\n')
  })

  it('QA-10 : seul shared/crypto importe une bibliothèque cryptographique, même dynamiquement', async () => {
    for (const source of ['node:crypto', 'crypto', '@hpke/core', '@noble/curves/ed25519']) {
      for (const code of [
        `import * as lib from '${source}'\nexport const l = lib\n`,
        `export * from '${source}'\n`,
        `export const l = import('${source}')\n`,
      ]) {
        await expectForbidden(FEATURE_MODEL, code)
        await expectForbidden(SHARED_API, code)
        await expectPermitted(SHARED_CRYPTO, code)
      }
    }
  })

  it('QA-10 : un import dynamique nomme sa cible en clair, pour que les frontières la voient', async () => {
    await expectForbidden(FEATURE_MODEL, "const m = 'node:crypto'\nexport const l = import(m)\n")
    await expectForbidden(SHARED_CRYPTO, "const m = 'x'\nexport const l = import(`./${m}`)\n")
    await expectPermitted(FEATURE_MODEL, "export const l = import('./useSignIn')\n")
  })
})

describe('ADR 0012, point 2 : puits XSS, styles en ligne et fuites, interdits partout', () => {
  it('SEC-02 : aucun rendu HTML brut, ni par React ni par le DOM', async () => {
    for (const file of [SHARED_UI, 'src/shared/crypto/View.tsx']) {
      await expectForbidden(
        file,
        'export const B = (h: string) => <div dangerouslySetInnerHTML={{ __html: h }} />\n',
      )
      await expectForbidden(
        file,
        'export const p = (h: string) => ({ dangerouslySetInnerHTML: { __html: h } })\n',
      )
    }
    for (const code of [
      'export const f = (e: Element, h: string) => { e.innerHTML = h }\n',
      'export const f = (e: Element, h: string) => { e.outerHTML = h }\n',
      "export const f = (e: Element, h: string) => { e.insertAdjacentHTML('beforeend', h) }\n",
      'export const f = (h: string) => { document.write(h) }\n',
      'export const f = (h: string) => { document.writeln(h) }\n',
      'export const f = (h: string) => document.createRange().createContextualFragment(h)\n',
      "export const f = (h: string) => new DOMParser().parseFromString(h, 'text/html')\n",
      'export const f = (e: Element, h: string) => { e.setHTMLUnsafe(h) }\n',
      'export const f = (e: HTMLIFrameElement, h: string) => { e.srcdoc = h }\n',
    ]) {
      await expectForbidden(SHARED_UI, code)
      await expectForbidden(SHARED_CRYPTO, code)
    }
    await expectPermitted(
      SHARED_UI,
      'export const f = (e: Element, t: string) => { e.textContent = t }\n',
    )
    await expectPermitted(SHARED_UI, 'export const B = (t: string) => <div>{t}</div>\n')
    await expectForbidden(
      SHARED_UI,
      "export const B = (h: string) => <iframe title='t' srcDoc={h} />\n",
    )
    await expectForbidden(SHARED_UI, "export const B = () => <a href='javascript:void 0'>x</a>\n")
    await expectPermitted(SHARED_UI, "export const B = () => <a href='/settings'>x</a>\n")
  })

  it('TRN-21 : aucun style en ligne', async () => {
    await expectForbidden(SHARED_UI, "export const B = () => <div style={{ color: 'red' }} />\n")
    await expectPermitted(SHARED_UI, "export const B = () => <div className='box' />\n")
  })

  it('A05 : ni eval ni Function', async () => {
    await expectForbidden(SHARED_UI, "export const v: unknown = eval('1')\n")
    await expectForbidden(SHARED_UI, "export const v = new Function('return 1')\n")
    await expectForbidden(SHARED_CRYPTO, "export const v: unknown = eval('1')\n")
  })

  it('SEC-21 : aucune écriture dans la console', async () => {
    await expectForbidden(SHARED_UI, "console.log('x')\nexport const v = 1\n")
    await expectForbidden(SHARED_CRYPTO, "console.error('x')\nexport const v = 1\n")
    await expectForbidden(SHARED_UI, "window.console.log('x')\nexport const v = 1\n")
  })

  it('KEY-41 : aucun stockage web persistant (localStorage, sessionStorage, Cache, OPFS), même dans shared/crypto', async () => {
    for (const file of [SHARED_UI, SHARED_API, SHARED_CRYPTO]) {
      await expectForbidden(file, "localStorage.setItem('k', 'v')\nexport const v = 1\n")
      await expectForbidden(file, "window.sessionStorage.setItem('k', 'v')\nexport const v = 1\n")
      await expectForbidden(file, "export const r = caches.open('seal')\n")
      await expectForbidden(file, "export const r = window.caches.open('seal')\n")
      await expectForbidden(file, 'export const r = navigator.storage.getDirectory()\n')
    }
  })

  it('KEY-40 : IndexedDB, coffre des clés, ne s’ouvre que depuis shared/crypto', async () => {
    for (const file of [FEATURE_MODEL, SHARED_API, SHARED_UI]) {
      await expectForbidden(file, "export const r = indexedDB.open('seal')\n")
      await expectForbidden(file, "export const r = window.indexedDB.open('seal')\n")
      await expectForbidden(file, "import { openDB } from 'idb'\nexport const o = openDB\n")
      await expectForbidden(file, "export const o = import('idb')\n")
    }
    await expectPermitted(SHARED_CRYPTO, "export const r = indexedDB.open('seal')\n")
    await expectPermitted(SHARED_CRYPTO, "import { openDB } from 'idb'\nexport const o = openDB\n")
  })
})

describe('ADR 0012, point 3 : rigueur', () => {
  it('QA-10 : un switch sur une union doit en traiter tous les cas', async () => {
    const SWITCH = (cases: string) =>
      `type Kind = 'a' | 'b'\nexport function f(k: Kind): number {\n  switch (k) {\n${cases}  }\n}\n`
    expect(
      await ruleIds(
        'src/shared/lib/kind.ts',
        SWITCH("    case 'a':\n      return 1\n"),
        typedEslint,
      ),
    ).toContain('@typescript-eslint/switch-exhaustiveness-check')
    expect(
      await ruleIds(
        'src/shared/lib/kind.ts',
        SWITCH("    case 'a':\n      return 1\n    case 'b':\n      return 2\n"),
        typedEslint,
      ),
    ).not.toContain('@typescript-eslint/switch-exhaustiveness-check')
  })
})

describe('Audit de la branche : frontières éprouvées par un cas refusé', () => {
  it('QA-10 : une tranche ne lit pas le segment d’une autre tranche de sa couche, même par son index.ts', async () => {
    await expectRefused('src/features/invite/model/x.ts', '@/features/sign-in/model/useSignIn')
    await expectRefused('src/features/sign-in/index.ts', '@/features/invite/model/invite')
  })

  it("QA-10 : l'API @x d'une entité est réservée à l'entité qu'elle nomme", async () => {
    await expectRefused(
      'src/entities/conversation/model/conversation.ts',
      '@/entities/user/@x/message',
    )
  })

  it('QA-10 : la couche widgets voit features, pas pages ni un autre widget ; features ne voit pas widgets', async () => {
    await expectAllowed('src/widgets/panel/ui/Panel.ts', '@/features/sign-in')
    await expectRefused('src/widgets/panel/ui/Panel.ts', '@/widgets/board')
    await expectRefused('src/widgets/panel/ui/Panel.ts', '@/pages/home')
    await expectRefused('src/features/sign-in/model/useSignIn.ts', '@/widgets/panel')
  })

  it("QA-10 : un segment n'importe pas l'index.ts de sa propre tranche, ni api/ le segment ui/", async () => {
    await expectRefused('src/features/sign-in/ui/Form.ts', '../index')
    await expectRefused('src/features/sign-in/api/signInRequest.ts', '../ui/Form')
  })

  it('QA-10 : ni shared/ui ni app n’atteignent le réseau ; api/ entre dans shared/ws par son index.ts', async () => {
    await expectRefused('src/shared/ui/Button.ts', '@/shared/api')
    await expectRefused('src/shared/ui/Button.ts', '@/shared/ws')
    await expectRefused('src/app/providers/Providers.ts', '@/shared/api')
    await expectRefused('src/features/sign-in/api/signInRequest.ts', '@/shared/ws/socket')
  })

  it("QA-10 : un segment model/ n'entre dans shared/crypto que par son index.ts", async () => {
    await expectRefused('src/features/sign-in/model/useSignIn.ts', '@/shared/crypto/canonical')
  })

  it("QA-10 : le point d'entrée n'importe que l'index.ts de app", async () => {
    await expectAllowed('src/main.tsx', '@/app')
    await expectRefused('src/main.tsx', '@/app/router')
  })

  it("QA-10 : une entité n'importe pas son propre @x, qui servirait de relais vers api/", async () => {
    await expectRefused('src/entities/user/model/user.ts', '../@x/conversation')
    await expectRefused('src/entities/user/ui/Card.ts', '../@x/conversation')
    await expectAllowed(
      'src/entities/conversation/model/conversation.ts',
      '@/entities/user/@x/conversation',
    )
  })
})

describe('Audit de la branche : contournements de l’outillage fermés', () => {
  it('QA-10 : sous src/, tout fichier de code autre que .ts et .tsx est refusé', async () => {
    for (const file of [
      'src/shared/lib/escape.mts',
      'src/shared/lib/escape.cts',
      'src/shared/lib/escape.js',
    ]) {
      expect(await ruleIds(file, "export const r = fetch('/x')\n")).toContain(
        'no-restricted-syntax',
      )
    }
  })

  it('QA-10 : import.meta.glob est refusé, il passerait sous toutes les frontières', async () => {
    await expectForbidden(
      SHARED_API,
      "export const m = import.meta.glob('../crypto/*.ts', { eager: true })\n",
    )
    await expectForbidden(FEATURE_MODEL, "export const m = import.meta.glob('../api/*.ts')\n")
  })

  it('KEY-41 : cookieStore est refusé partout, shared/api compris', async () => {
    for (const file of [FEATURE_MODEL, SHARED_API, SHARED_CRYPTO]) {
      await expectForbidden(file, "export const t = cookieStore.get('XSRF-TOKEN')\n")
      await expectForbidden(file, "export const t = window.cookieStore.set('k', 'v')\n")
    }
  })

  it('QA-10 : aucune autre référence à la fenêtre ne rouvre un accès calculé à l’objet global', async () => {
    for (const code of [
      "const k = 'crypto'\nexport const c = window.window[k]\n",
      "const k = 'crypto'\nexport const c = globalThis.self[k]\n",
      "export const c = frames['cry' + 'pto']\n",
      "export const c = Reflect.get(top, 'fetch')\n",
      "const n = 'indexedDB'\nexport const c = parent[n]\n",
      "const k = 'fetch'\nexport const c = window.top[k]\n",
      'export const c = opener\n',
      "const k = 'crypto'\nexport const c = (f: HTMLIFrameElement) => f.contentWindow?.[k]\n",
    ]) {
      await expectForbidden(SHARED_API, code)
    }
    await expectPermitted(FEATURE_MODEL, 'export const y = (rect: DOMRect) => rect.top\n')
    await expectPermitted(
      FEATURE_MODEL,
      'export const y = (rect: DOMRect) => { const { top } = rect; return top }\n',
    )
    await expectPermitted(
      FEATURE_MODEL,
      'export const p = (node: { parent: number }) => node.parent\n',
    )
  })

  it('QA-10 : shared/crypto ne se réexporte pas, ni par l’alias ni par un chemin relatif', async () => {
    await expectForbidden(FEATURE_MODEL, "export * from '@/shared/crypto'\n")
    await expectForbidden(FEATURE_MODEL, "export { value } from '../../../shared/crypto'\n")
  })

  it('KEY-40 : une bibliothèque ne s’atteint pas par son chemin dans node_modules', async () => {
    await expectForbidden(
      SHARED_API,
      "import * as h from '../../../node_modules/@hpke/core/esm/mod.js'\nexport const v = h\n",
    )
    await expectForbidden(
      SHARED_API,
      "export const v = import('@/../node_modules/idb/build/index.js')\n",
    )
    await expectForbidden(
      FEATURE_MODEL,
      "import { openDB } from 'idb/with-async-ittr'\nexport const o = openDB\n",
    )
    await expectForbidden(FEATURE_MODEL, "export const o = import('idb/with-async-ittr')\n")
    await expectPermitted(
      SHARED_CRYPTO,
      "import { openDB } from 'idb/with-async-ittr'\nexport const o = openDB\n",
    )
  })

  it('SEC-21 : la console ne s’atteint pas par une décomposition ni un alias, sauf pour l’espionner dans un test', async () => {
    await expectForbidden(SHARED_UI, "const { error } = console\nerror('x')\nexport const v = 1\n")
    await expectForbidden(SHARED_CRYPTO, "const out = console\nout.info('x')\nexport const v = 1\n")
    await expectPermitted(
      'src/shared/ui/Box.test.tsx',
      "import { vi } from 'vitest'\nexport const spy = vi.spyOn(console, 'error')\n",
    )
    await expectPermitted(
      'src/shared/crypto/keys.test.ts',
      "import { vi } from 'vitest'\nexport const spy = vi.spyOn(console, 'error')\n",
    )
  })

  it("SEC-21 : aucun fichier n'importe un test, qui sortirait console et Vitest de leur exemption", async () => {
    await expectForbidden(
      FEATURE_MODEL,
      "import { out } from './leak.test'\nexport const v = out\n",
    )
    await expectForbidden(
      SHARED_UI,
      "import { out } from '@/shared/ui/Box.test.tsx'\nexport const v = out\n",
    )
  })

  it('QA-10 : une directive eslint-disable en ligne ne lève aucune règle', async () => {
    const rules = await ruleIds(
      FEATURE_MODEL,
      "// eslint-disable-next-line no-restricted-globals\nexport const r = fetch('/x')\n",
    )
    expect(rules).toContain('no-restricted-globals')
  })

  it('KEY-41 : OPFS reste interdit sur tout objet ; navigator.storage.persist() est permis dans shared/crypto seulement', async () => {
    await expectForbidden(
      FEATURE_MODEL,
      'export const d = window.navigator.storage.getDirectory()\n',
    )
    await expectForbidden(SHARED_CRYPTO, 'export const d = navigator.storage.getDirectory()\n')
    await expectForbidden(FEATURE_MODEL, 'export const p = navigator.storage.persist()\n')
    await expectPermitted(SHARED_CRYPTO, 'export const p = navigator.storage.persist()\n')
  })

  it('SEC-02 : Document.parseHTMLUnsafe est un puits HTML', async () => {
    await expectForbidden(
      SHARED_UI,
      'export const f = (h: string) => Document.parseHTMLUnsafe(h)\n',
    )
  })
})
