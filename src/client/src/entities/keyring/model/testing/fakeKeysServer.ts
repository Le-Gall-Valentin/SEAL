/**
 * Serveur des clés en mémoire, fidèle aux règles de l'ADR 0025 que le client rencontre : un seul certificat (403
 * ensuite), sous-clé liée au certificat courant, créée à 5 minutes au plus de l'horloge et sous un ekId jamais publié
 * (400 sinon), coffre en versions successives (409 sinon), réinitialisation qui remplace coffre et certificat (ADR 0036). `subkeys` est la liste servie : un test la vide pour imiter
 * un serveur qui tait une sous-clé publiée. Une panne se règle avant ou après l'effet, pour imiter une réponse perdue. Réservé aux tests.
 */
import { ApiError, NetworkError } from '@/shared/lib'
import type { CertificateView, IKeysApi, SubkeyView, VaultView } from '../../api/IKeysApi'

const CLOCK_SKEW_MS = 5 * 60_000

type Method = keyof IKeysApi

export interface FakeKeysServer extends IKeysApi {
  readonly calls: Method[]
  vault: VaultView | undefined
  certificate: CertificateView | undefined
  subkeys: SubkeyView[]
  /** La prochaine requête `method` échoue sur le réseau, avant ou après son effet. */
  fail(method: Method, when: 'before' | 'after'): void
  /** La prochaine lecture des clés ne voit pas le certificat, publié entre-temps par un autre onglet. */
  hideCertificateOnce(): void
  /** La prochaine réinitialisation trouve le mode sudo fermé : 403 REAUTH_REQUIRED, sans effet. */
  closeSudoOnce(): void
}

/** `issued` : le certificat que l'Autorité émet à la publication de l'identité. */
export function fakeKeysServer(issued: CertificateView, now: () => number): FakeKeysServer {
  const failures = new Map<Method, 'before' | 'after'>()
  const publishedEkIds = new Set<string>()
  let hidden = false
  let sudoClosed = false

  async function handle<T>(method: Method, effect: () => T): Promise<T> {
    server.calls.push(method)
    const failure = failures.get(method)
    failures.delete(method)
    if (failure === 'before') {
      throw new NetworkError()
    }
    const result = effect()
    if (failure === 'after') {
      throw new NetworkError()
    }
    return Promise.resolve(result)
  }

  const server: FakeKeysServer = {
    calls: [],
    vault: undefined,
    certificate: undefined,
    subkeys: [],
    fail(method, when) {
      failures.set(method, when)
    },
    hideCertificateOnce() {
      hidden = true
    },
    closeSudoOnce() {
      sudoClosed = true
    },
    createChallenge: () => handle('createChallenge', () => 'A'.repeat(43)),
    publishIdentity: () =>
      handle('publishIdentity', () => {
        if (server.certificate !== undefined) {
          throw new ApiError(403, 'FORBIDDEN')
        }
        server.certificate = issued
        return issued
      }),
    resetIdentity: ({ vault, ikPublic }) =>
      handle('resetIdentity', () => {
        if (sudoClosed) {
          sudoClosed = false
          throw new ApiError(403, 'REAUTH_REQUIRED')
        }
        if (server.vault === undefined) {
          throw new ApiError(404, 'KEY_NOT_FOUND')
        }
        if (vault.vaultVersion !== server.vault.vaultVersion + 1) {
          return 'conflict' as const
        }
        if (server.certificate?.ikPublic === ikPublic) {
          throw new ApiError(403, 'FORBIDDEN')
        }
        server.vault = vault
        server.certificate = issued
        server.subkeys = []
        return issued
      }),
    publishSubkey: ({ certSerial, ...subkey }) =>
      handle('publishSubkey', () => {
        if (server.certificate?.serial !== certSerial) {
          throw new ApiError(404, 'KEY_NOT_FOUND')
        }
        if (Math.abs(now() - subkey.createdAt) > CLOCK_SKEW_MS) {
          throw new ApiError(400, 'VALIDATION_FAILED', { fields: ['createdAt'] })
        }
        if (publishedEkIds.has(subkey.ekId)) {
          throw new ApiError(400, 'VALIDATION_FAILED', { fields: ['ekId'] })
        }
        publishedEkIds.add(subkey.ekId)
        server.subkeys.push(subkey)
      }),
    readVault: () => handle('readVault', () => server.vault),
    saveVault: (vault) =>
      handle('saveVault', () => {
        const expected = (server.vault?.vaultVersion ?? 0) + 1
        if (vault.vaultVersion !== expected) {
          return 'conflict' as const
        }
        server.vault = vault
        return 'saved' as const
      }),
    readOwnKeys: () =>
      handle('readOwnKeys', () => {
        if (hidden) {
          hidden = false
          return undefined
        }
        return server.certificate === undefined
          ? undefined
          : {
              certificate: server.certificate,
              subkeys: server.subkeys
                .filter((subkey) => subkey.expiresAt > now())
                .toSorted((left, right) => right.createdAt - left.createdAt),
            }
      }),
  }
  return server
}
