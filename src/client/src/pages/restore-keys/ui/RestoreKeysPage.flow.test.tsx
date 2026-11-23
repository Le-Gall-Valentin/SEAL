import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { LoginMethodsApiProvider } from '@/entities/account'
import {
  type CertificateView,
  type IKeysApi,
  KEYS_LOCK,
  KeysApiProvider,
  KeyStoreProvider,
  type VaultView,
} from '@/entities/keyring'
import { OAuthProvidersApiProvider } from '@/entities/oauth-provider'
import { PasswordBlocklistProvider } from '@/entities/password'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import { ReauthApiProvider } from '@/features/reauthenticate'
import { ApiError } from '@/shared/lib'
import { RestoreKeysPage } from './RestoreKeysPage'

// Les tests n'ont pas la clé de l'Autorité : seule la vérification de la signature du certificat servi est simulée.
// Dialogues, génération de la clé, ré-authentification, relance et publication de la sous-clé sont les vrais.
vi.mock('@/shared/crypto', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  verifyOwnCertificate: () => Promise.resolve(),
  storeOwnCertificate: () => Promise.resolve(),
}))

const OWNER = { userId: '0192f7a1-ffff-7000-8000-00000000a11c', username: 'valentin.dupont' }

/** Coffre de l'identité perdue, en version 1 : la réinitialisation l'ouvre pas, elle le remplace (version 2). */
const LOST_VAULT: VaultView = {
  vaultVersion: 1,
  salt: 'A'.repeat(43),
  nonceK: 'A'.repeat(16),
  wrappedVdk: 'A'.repeat(64),
  nonceB: 'A'.repeat(16),
  blob: 'A'.repeat(22),
}

/** Serveur des clés : le mode sudo fermé au premier envoi, puis la nouvelle identité certifiée telle qu'envoyée. */
function keysServer() {
  const calls: string[] = []
  const sent: { vaultVersion: number }[] = []
  let served: CertificateView | undefined
  let sudoClosed = true
  const api: IKeysApi = {
    createChallenge: () => {
      calls.push('createChallenge')
      return Promise.resolve('A'.repeat(43))
    },
    publishIdentity: () => Promise.reject(new Error('KEY-34 : la réinitialisation ne publie pas')),
    resetIdentity: (body) => {
      calls.push('resetIdentity')
      if (sudoClosed) {
        sudoClosed = false
        return Promise.reject(new ApiError(403, 'REAUTH_REQUIRED'))
      }
      sent.push({ vaultVersion: body.vault.vaultVersion })
      served = {
        serial: 'A'.repeat(22),
        issuerKeyId: 'If4x36FUomE',
        subjectId: OWNER.userId,
        username: OWNER.username,
        ikPublic: body.ikPublic,
        notBefore: Date.now(),
        notAfter: Date.now() + 397 * 86_400_000,
        usage: 3,
        signature: 'A'.repeat(86),
      }
      return Promise.resolve(served)
    },
    publishSubkey: () => {
      calls.push('publishSubkey')
      return Promise.resolve()
    },
    readVault: () => Promise.resolve(LOST_VAULT),
    saveVault: () => Promise.reject(new Error('KEY-34 : le coffre part avec la réinitialisation')),
    readOwnKeys: () =>
      Promise.resolve(served === undefined ? undefined : { certificate: served, subkeys: [] }),
  }
  return { api, calls, sent }
}

type KeyStore = Parameters<typeof KeyStoreProvider>[0]['api']
type Keyring = NonNullable<Awaited<ReturnType<KeyStore['loadKeyring']>>>

/** Trousseau de l'appareil en mémoire : une page n'atteint pas shared/crypto (ADR 0012). */
function memoryStore(): KeyStore {
  const keyrings = new Map<string, Keyring>()
  return {
    loadKeyring: (userId) => Promise.resolve(keyrings.get(userId)),
    saveKeyring: (keyring) => {
      keyrings.set(keyring.userId, keyring)
      return Promise.resolve()
    },
    loadCertificate: () => Promise.resolve(undefined),
    loadAuthority: () => Promise.resolve(undefined),
    close: () => undefined,
  }
}

async function renderPage(api: IKeysApi, reauthenticate: (password: string) => Promise<void>) {
  const store = memoryStore()
  const onRestored = vi.fn()
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  const screen = await render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { gcTime: 0 } } })
      }
    >
      <SessionApiProvider api={sessionApi}>
        <LogoutApiProvider api={{ logout: () => Promise.resolve() }}>
          <KeysApiProvider api={api}>
            <KeyStoreProvider api={store}>
              <PasswordBlocklistProvider api={{ load: () => Promise.resolve([]) }}>
                <LoginMethodsApiProvider
                  api={{
                    loginMethods: () => Promise.resolve({ passwordSet: true, providers: [] }),
                  }}
                >
                  <OAuthProvidersApiProvider api={{ providers: () => Promise.resolve([]) }}>
                    <ReauthApiProvider api={{ reauthenticate }}>
                      <MemoryRouter initialEntries={['/parametres']}>
                        <RestoreKeysPage owner={OWNER} onRestored={onRestored} />
                      </MemoryRouter>
                    </ReauthApiProvider>
                  </OAuthProvidersApiProvider>
                </LoginMethodsApiProvider>
              </PasswordBlocklistProvider>
            </KeyStoreProvider>
          </KeysApiProvider>
        </LogoutApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { screen, onRestored }
}

type Screen = Awaited<ReturnType<typeof renderPage>>['screen']

async function confirmReset(screen: Screen) {
  await screen.getByRole('button', { name: 'Réinitialiser mon identité…' }).click()
  await screen
    .getByRole('alertdialog', { name: 'Réinitialiser votre identité ?' })
    .getByRole('button', { name: 'Réinitialiser', exact: true })
    .click()
}

/** Dialogue-Cle-Recuperation : les huit groupes lus, les deux groupes demandés ressaisis, puis « Terminer ». */
async function noteTheNewKey(screen: Screen) {
  await expect
    .element(screen.getByRole('dialog', { name: 'Votre clé de récupération' }))
    .toBeVisible()
  const groups = screen
    .getByRole('list', { name: 'Clé de récupération, 8 groupes de 4 caractères' })
    .getByRole('listitem')
    .elements()
    .map((item) => item.lastElementChild?.textContent ?? '')
  await screen.getByRole('button', { name: 'J’ai noté ma clé' }).click()
  for (let number = 1; number <= 8; number += 1) {
    const field = screen.getByLabelText(`Groupe ${String(number)}`, { exact: true })
    if (field.query() !== null) {
      await field.fill(groups[number - 1] ?? '')
    }
  }
  await screen.getByRole('button', { name: 'Terminer' }).click()
}

describe('KEY-34, AUTH-55, ADR 0036 : réinitialisation depuis Restauration-Appareil, de bout en bout côté client', () => {
  it('KEY-34, AUTH-55 : confirmation, nouvelle clé notée, 403, « Confirmez votre identité », relance avec la même clé, puis la zone du compte relit les clés', async () => {
    const server = keysServer()
    const reauthenticate = vi.fn<(password: string) => Promise<void>>(() => Promise.resolve())
    const { screen, onRestored } = await renderPage(server.api, reauthenticate)

    await confirmReset(screen)
    await noteTheNewKey(screen)
    const reauth = screen.getByRole('dialog', { name: 'Confirmez votre identité' })
    await reauth.getByLabelText('Mot de passe', { exact: true }).fill('phrase secrète')
    await reauth.getByRole('button', { name: 'Confirmer' }).click()

    await expect.poll(() => server.calls.length).toBe(5)
    expect(server.calls).toEqual([
      'createChallenge',
      'resetIdentity',
      'createChallenge',
      'resetIdentity',
      'publishSubkey',
    ])
    // ADR 0036, point 2 : le nouveau coffre suit la version enregistrée.
    expect(server.sent).toEqual([{ vaultVersion: 2 }])
    expect(reauthenticate).toHaveBeenCalledWith('phrase secrète')
    await expect.poll(() => onRestored.mock.calls.length).toBe(1)
  })

  it('ADR 0029 (point 13), ADR 0036 : tant qu’un autre onglet tient le verrou des clés, l’attente d’Etats-Cles, puis la nouvelle clé', async () => {
    const server = keysServer()
    let free: () => void = () => undefined
    const holder = navigator.locks.request(
      KEYS_LOCK,
      () =>
        new Promise<void>((resolve) => {
          free = resolve
        }),
    )
    const { screen } = await renderPage(server.api, () => Promise.resolve())

    await confirmReset(screen)

    await expect
      .element(screen.getByText('Vos clés sont en cours de création dans un autre onglet.'))
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    free()
    await holder
    await expect
      .element(screen.getByRole('dialog', { name: 'Votre clé de récupération' }))
      .toBeVisible()
    expect(server.calls).toEqual([])
  })
})
