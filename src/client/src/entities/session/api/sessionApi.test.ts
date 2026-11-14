import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/lib'
import { sessionApi } from './sessionApi'

const PENDING = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@exemple.ca',
  status: 'PENDING_EMAIL',
  factors: ['PASSWORD'],
  verification: { codeExpiresAt: '2026-11-13T20:10:00Z', resendsRemaining: 2 },
}

function respond(status: number, body: unknown) {
  // Signature de fetch telle que shared/api l'appelle : adresse en texte, puis options.
  const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
    Promise.resolve(
      new Response(body === null ? null : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
    ),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('entities/session : adaptateur de GET /api/v1/auth/me', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('AUTH-16 : un compte en attente donne son état et sa vérification, dates lues en UTC', async () => {
    const fetchMock = respond(200, PENDING)

    const account = await sessionApi.me()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/me')
    expect(account).toEqual({
      id: PENDING.id,
      username: 'valentin',
      displayName: 'Valentin',
      email: 'valentin@exemple.ca',
      status: 'PENDING_EMAIL',
      verification: { codeExpiresAt: new Date('2026-11-13T20:10:00Z'), resendsRemaining: 2 },
    })
  })

  it('AUTH-16 : un compte actif n’a pas de vérification, et un code mort a une expiration nulle', async () => {
    respond(200, { ...PENDING, status: 'ACTIVE', verification: null })
    expect((await sessionApi.me())?.verification).toBeNull()

    respond(200, { ...PENDING, verification: { codeExpiresAt: null, resendsRemaining: 0 } })
    expect((await sessionApi.me())?.verification).toEqual({
      codeExpiresAt: null,
      resendsRemaining: 0,
    })
  })

  it('AUTH-16 : 401 signifie « anonyme », sans erreur', async () => {
    respond(401, { status: 401, code: 'UNAUTHENTICATED' })

    expect(await sessionApi.me()).toBeNull()
  })

  it('A10 : toute autre erreur remonte, typée', async () => {
    respond(500, { status: 500, code: 'INTERNAL_ERROR' })

    await expect(sessionApi.me()).rejects.toBeInstanceOf(ApiError)
  })

  it.each([
    ['un état inconnu', { ...PENDING, status: 'DELETED' }],
    ['un identifiant qui n’est pas un UUID', { ...PENDING, id: '42' }],
    [
      'une date illisible',
      { ...PENDING, verification: { codeExpiresAt: 'demain', resendsRemaining: 1 } },
    ],
    [
      'un nombre de renvois négatif',
      { ...PENDING, verification: { codeExpiresAt: null, resendsRemaining: -1 } },
    ],
    ['un champ manquant', { ...PENDING, email: undefined }],
  ])('SEC-01 : %s est refusé par le schéma', async (_cas, body) => {
    respond(200, body)

    await expect(sessionApi.me()).rejects.toMatchObject({ code: 'INTERNAL_ERROR' })
  })

  it('AUTH-60 : reloadCsrf lit GET /api/v1/csrf', async () => {
    const fetchMock = respond(204, null)

    await sessionApi.reloadCsrf()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/csrf')
  })
})
