import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/lib'
import { authorityApi } from './authorityApi'

// Autorité des vecteurs (vectors/identity.json, section authority).
const AUTHORITY = {
  publicKey: '11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo',
  keyId: 'If4x36FUomE',
  fingerprint: '21FE31DFA154A261626BF854046FD2271B7BED4B6ABE45AA58877EF47F9721B9',
}

function respond(status: number, body: unknown) {
  const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
    Promise.resolve(
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
    ),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('entities/authority : adaptateur de GET /api/v1/pki/ca (KEY-11)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('KEY-11 : rend la clé, son identifiant et son empreinte tels que servis', async () => {
    const fetchMock = respond(200, AUTHORITY)

    await expect(authorityApi.fetchAuthority()).resolves.toEqual(AUTHORITY)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/pki/ca')
  })

  it('A10 : le signal d’annulation de TanStack Query atteint fetch', async () => {
    const fetchMock = respond(200, AUTHORITY)
    const controller = new AbortController()

    await authorityApi.fetchAuthority(controller.signal)

    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
  })

  it.each([
    ['un champ en trop', { ...AUTHORITY, extra: 1 }],
    ['une clé de 31 octets', { ...AUTHORITY, publicKey: AUTHORITY.publicKey.slice(1) }],
    ['une clé en base64 standard', { ...AUTHORITY, publicKey: `${AUTHORITY.publicKey.slice(1)}+` }],
    ['un keyId de 7 octets', { ...AUTHORITY, keyId: AUTHORITY.keyId.slice(2) }],
    [
      'une empreinte en minuscules',
      { ...AUTHORITY, fingerprint: AUTHORITY.fingerprint.toLowerCase() },
    ],
    ['une empreinte tronquée', { ...AUTHORITY, fingerprint: AUTHORITY.fingerprint.slice(1) }],
    ['un champ absent', { publicKey: AUTHORITY.publicKey, keyId: AUTHORITY.keyId }],
  ])('SEC-01 : %s est refusé', async (_, body) => {
    respond(200, body)

    await expect(authorityApi.fetchAuthority()).rejects.toBeInstanceOf(ApiError)
  })

  it('A10 : une erreur du serveur remonte, typée', async () => {
    respond(500, { status: 500, code: 'INTERNAL_ERROR' })

    await expect(authorityApi.fetchAuthority()).rejects.toBeInstanceOf(ApiError)
  })
})
