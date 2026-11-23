import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'
import { ApiError } from '@/shared/lib'

const calls: { path: string; options: unknown }[] = []
let response: unknown
let failure: Error | undefined

// Doublure de shared/api fidèle à request() : la réponse passe par le schéma de l'adaptateur (SEC-01).
vi.mock('@/shared/api', () => ({
  request: (path: string, schema: z.ZodType, options?: unknown) => {
    calls.push({ path, options })
    if (failure !== undefined) {
      return Promise.reject(failure)
    }
    const parsed = schema.safeParse(response)
    return parsed.success
      ? Promise.resolve(parsed.data)
      : Promise.reject(new ApiError(200, 'INTERNAL_ERROR'))
  },
  requestNoContent: (path: string, options?: unknown) => {
    calls.push({ path, options })
    return failure === undefined ? Promise.resolve() : Promise.reject(failure)
  },
}))

const { keysApi } = await import('./keysApi')

const USER_ID = '0192f7a1-ffff-7000-8000-00000000a11c'
const B16 = 'AAAAAAAAAAAAAAAAAAAAAA'
const B32 = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
const B64 = 'A'.repeat(86)
const KEM = 'DHKEM-X25519-HKDF-SHA256'

const CERTIFICATE = {
  serial: B16,
  issuerKeyId: 'If4x36FUomE',
  subjectId: USER_ID,
  username: 'valentin.dupont',
  ikPublic: B32,
  notBefore: '2026-11-19T12:00:00.123Z',
  notAfter: '2027-12-21T12:00:00.123Z',
  usage: 3,
  signature: B64,
}

const SUBKEY = {
  ekId: B16,
  kem: KEM,
  ekPublic: B32,
  createdAt: '2026-11-19T12:00:00Z',
  expiresAt: '2027-01-03T12:00:00Z',
  signature: B64,
}

const VAULT = {
  vaultVersion: 1,
  salt: B32,
  nonceK: 'AAAAAAAAAAAAAAAA',
  wrappedVdk: 'A'.repeat(64),
  nonceB: 'AAAAAAAAAAAAAAAA',
  blob: B16,
}

afterEach(() => {
  calls.length = 0
  response = undefined
  failure = undefined
})

describe('entities/keyring : adaptateur de l’API des clés (KEY-02, KEY-03, KEY-05, KEY-32, KEY-33)', () => {
  it('KEY-03 : POST /keys/challenge rend le défi de 32 octets', async () => {
    response = { challenge: B32, expiresAt: '2026-11-19T12:05:00.123Z' }

    await expect(keysApi.createChallenge()).resolves.toBe(B32)
    expect(calls).toEqual([{ path: '/api/v1/keys/challenge', options: { method: 'POST' } }])
  })

  it('KEY-03 : l’expiration du défi, qui n’est pas signée, peut porter des nanosecondes', async () => {
    response = { challenge: B32, expiresAt: '2026-11-19T12:05:00.123456789Z' }

    await expect(keysApi.createChallenge()).resolves.toBe(B32)
  })

  it('KEY-03, KEY-04 : PUT /keys/identity envoie la preuve et rend le certificat, dates en millisecondes', async () => {
    response = CERTIFICATE
    const proof = { ikPublic: B32, challenge: B32, signature: B64 }

    await expect(keysApi.publishIdentity(proof)).resolves.toEqual({
      ...CERTIFICATE,
      notBefore: Date.UTC(2026, 10, 19, 12, 0, 0, 123),
      notAfter: Date.UTC(2027, 11, 21, 12, 0, 0, 123),
    })
    expect(calls).toEqual([
      { path: '/api/v1/keys/identity', options: { method: 'PUT', body: proof } },
    ])
  })

  it('KEY-05 : POST /keys/subkeys envoie la liaison, dates converties en ISO 8601 à la milliseconde', async () => {
    const createdAt = Date.UTC(2026, 10, 19, 12, 0, 0, 7)

    await keysApi.publishSubkey({
      certSerial: B16,
      ekId: B16,
      kem: KEM,
      ekPublic: B32,
      createdAt,
      expiresAt: createdAt + 45 * 86_400_000,
      signature: B64,
    })

    expect(calls).toEqual([
      {
        path: '/api/v1/keys/subkeys',
        options: {
          method: 'POST',
          body: {
            certSerial: B16,
            ekId: B16,
            kem: KEM,
            ekPublic: B32,
            createdAt: '2026-11-19T12:00:00.007Z',
            expiresAt: '2027-01-03T12:00:00.007Z',
            signature: B64,
          },
        },
      },
    ])
  })

  it('KEY-33 : GET /keys/vault rend le coffre, et rien sur 404 KEY_NOT_FOUND', async () => {
    response = VAULT
    await expect(keysApi.readVault()).resolves.toEqual(VAULT)
    expect(calls[0]?.path).toBe('/api/v1/keys/vault')

    failure = new ApiError(404, 'KEY_NOT_FOUND')
    await expect(keysApi.readVault()).resolves.toBeUndefined()
  })

  it('KEY-32 : PUT /keys/vault rend saved, ou conflict sur 409 VAULT_VERSION_CONFLICT', async () => {
    await expect(keysApi.saveVault(VAULT)).resolves.toBe('saved')
    expect(calls).toEqual([{ path: '/api/v1/keys/vault', options: { method: 'PUT', body: VAULT } }])

    failure = new ApiError(409, 'VAULT_VERSION_CONFLICT')
    await expect(keysApi.saveVault(VAULT)).resolves.toBe('conflict')
  })

  it('KEY-34 : POST /keys/identity/reset envoie preuve et coffre et rend le certificat, ou conflict sur 409', async () => {
    response = CERTIFICATE
    const body = {
      ikPublic: B32,
      challenge: B32,
      signature: B64,
      vault: { ...VAULT, vaultVersion: 2 },
    }

    await expect(keysApi.resetIdentity(body)).resolves.toEqual({
      ...CERTIFICATE,
      notBefore: Date.UTC(2026, 10, 19, 12, 0, 0, 123),
      notAfter: Date.UTC(2027, 11, 21, 12, 0, 0, 123),
    })
    expect(calls).toEqual([
      { path: '/api/v1/keys/identity/reset', options: { method: 'POST', body } },
    ])

    failure = new ApiError(409, 'VAULT_VERSION_CONFLICT')
    await expect(keysApi.resetIdentity(body)).resolves.toBe('conflict')
    failure = new ApiError(403, 'REAUTH_REQUIRED')
    await expect(keysApi.resetIdentity(body)).rejects.toMatchObject({ code: 'REAUTH_REQUIRED' })
    failure = undefined
    response = { ...CERTIFICATE, extra: true }
    await expect(keysApi.resetIdentity(body)).rejects.toMatchObject({ code: 'INTERNAL_ERROR' })
  })

  it('KEY-33 : GET /users/{id}/keys rend certificat et sous-clés en millisecondes, et rien sur 404', async () => {
    response = { certificate: CERTIFICATE, subkeys: [SUBKEY] }

    await expect(keysApi.readOwnKeys(USER_ID)).resolves.toEqual({
      certificate: {
        ...CERTIFICATE,
        notBefore: Date.UTC(2026, 10, 19, 12, 0, 0, 123),
        notAfter: Date.UTC(2027, 11, 21, 12, 0, 0, 123),
      },
      subkeys: [
        {
          ...SUBKEY,
          createdAt: Date.UTC(2026, 10, 19, 12),
          expiresAt: Date.UTC(2027, 0, 3, 12),
        },
      ],
    })
    expect(calls[0]?.path).toBe(`/api/v1/users/${USER_ID}/keys`)

    failure = new ApiError(404, 'KEY_NOT_FOUND')
    await expect(keysApi.readOwnKeys(USER_ID)).resolves.toBeUndefined()
  })

  it('SEC-01 : un champ en trop, une longueur fausse ou une date au-delà de la milliseconde sont refusés', async () => {
    for (const refused of [
      { ...CERTIFICATE, extra: 1 },
      { ...CERTIFICATE, serial: B32 },
      { ...CERTIFICATE, notBefore: '2026-11-19T12:00:00.123456Z' },
      { ...CERTIFICATE, notBefore: '2026-11-19T12:00:00.123+01:00' },
      { ...CERTIFICATE, usage: 256 },
    ]) {
      response = refused
      await expect(
        keysApi.publishIdentity({ ikPublic: B32, challenge: B32, signature: B64 }),
      ).rejects.toMatchObject({ code: 'INTERNAL_ERROR' })
    }
    response = { certificate: CERTIFICATE, subkeys: [{ ...SUBKEY, kem: 'X-WING' }] }
    await expect(keysApi.readOwnKeys(USER_ID)).rejects.toMatchObject({ code: 'INTERNAL_ERROR' })
    response = { ...VAULT, blob: '' }
    await expect(keysApi.readVault()).rejects.toMatchObject({ code: 'INTERNAL_ERROR' })
  })

  it('A10 : les autres refus remontent tels quels (403 FORBIDDEN, 422 INVALID_SIGNATURE)', async () => {
    failure = new ApiError(403, 'FORBIDDEN')
    await expect(
      keysApi.publishIdentity({ ikPublic: B32, challenge: B32, signature: B64 }),
    ).rejects.toMatchObject({ code: 'FORBIDDEN' })
    failure = new ApiError(422, 'INVALID_SIGNATURE')
    await expect(keysApi.saveVault(VAULT)).rejects.toMatchObject({ code: 'INVALID_SIGNATURE' })
    failure = new ApiError(403, 'FORBIDDEN')
    await expect(keysApi.readOwnKeys(USER_ID)).rejects.toMatchObject({ code: 'FORBIDDEN' })
  })
})
