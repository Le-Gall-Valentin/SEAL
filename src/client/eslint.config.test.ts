import { fileURLToPath } from 'node:url'
import { ESLint } from 'eslint'
import { createTypeScriptImportResolver } from 'eslint-import-resolver-typescript'
import { configs as tseslintConfigs } from 'typescript-eslint'
import { describe, expect, it } from 'vitest'

// La configuration du dépôt, appliquée au mini-projet témoin eslint-fixtures/ avec le typage coupé : chaque frontière
// de l'ADR 0012 doit refuser son cas interdit et accepter son cas permis (comme les règles ArchUnit du serveur).
const FIXTURES = fileURLToPath(new URL('./eslint-fixtures', import.meta.url))
const FIXTURES_TSCONFIG = `${FIXTURES}/tsconfig.json`

const eslint = new ESLint({
  cwd: FIXTURES,
  overrideConfigFile: fileURLToPath(new URL('./eslint.config.js', import.meta.url)),
  overrideConfig: [
    { ignores: ['!**/eslint-fixtures/**'] },
    tseslintConfigs.disableTypeChecked,
    {
      languageOptions: { parserOptions: { projectService: false, project: null } },
      settings: {
        'boundaries/root-path': FIXTURES,
        'import/resolver': { typescript: { alwaysTryTypes: true, project: FIXTURES_TSCONFIG } },
        'import-x/resolver-next': [createTypeScriptImportResolver({ project: FIXTURES_TSCONFIG })],
      },
    },
  ],
})

async function ruleIds(file: string, code: string): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: `${FIXTURES}/${file}` })
  return (result?.messages ?? []).flatMap((message) => (message.ruleId ? [message.ruleId] : []))
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
