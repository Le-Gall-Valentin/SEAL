// Dans Chromium : le jeton CSRF se lit dans le vrai document.cookie (AUTH-60).
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import { ApiError, NetworkError } from '@/shared/lib'
import { reloadCsrf, request, requestNoContent } from './http'

const ACCOUNT = z.object({ id: z.string(), username: z.string() })

function problem(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...headers },
  })
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function setCsrfCookie(value: string | null) {
  document.cookie =
    value === null
      ? 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
      : `XSRF-TOKEN=${value}; path=/`
}

function stubFetch(handler: (url: string, init: RequestInit) => Response | Promise<Response>) {
  // shared/api n'appelle fetch qu'avec une adresse en texte.
  const fetchMock = vi.fn((input: string, init?: RequestInit) =>
    Promise.resolve(handler(input, init ?? {})),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function headersOf(call: unknown[] | undefined): Headers {
  const init = call?.[1] as RequestInit | undefined
  return new Headers(init?.headers)
}

async function rejection(promise: Promise<unknown>): Promise<unknown> {
  try {
    await promise
  } catch (error) {
    return error
  }
  throw new Error('la requête aurait dû échouer')
}

describe('shared/api : client HTTP de SEAL', () => {
  beforeEach(() => {
    setCsrfCookie('jeton-csrf')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    setCsrfCookie(null)
  })

  it('AUTH-60 : une lecture GET part sans en-tête X-XSRF-TOKEN et rend la réponse validée', async () => {
    const fetchMock = stubFetch(() => json({ id: 'u1', username: 'valentin', extra: 1 }))

    const account = await request('/api/v1/auth/me', ACCOUNT)

    expect(account).toEqual({ id: 'u1', username: 'valentin' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/v1/auth/me')
    expect(init?.method).toBe('GET')
    expect(init?.credentials).toBe('same-origin')
    expect(headersOf(fetchMock.mock.calls[0]).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('AUTH-60 : une requête POST porte le jeton du cookie XSRF-TOKEN et un corps JSON', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 204 }))

    await requestNoContent('/api/v1/auth/login', {
      method: 'POST',
      body: { login: 'valentin', password: 'secret' },
    })

    const headers = headersOf(fetchMock.mock.calls[0])
    expect(headers.get('X-XSRF-TOKEN')).toBe('jeton-csrf')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe('{"login":"valentin","password":"secret"}')
  })

  it('AUTH-60 : sans cookie XSRF-TOKEN, le jeton est rechargé avant la requête non sûre', async () => {
    setCsrfCookie(null)
    const fetchMock = stubFetch((url) => {
      if (url === '/api/v1/csrf') {
        setCsrfCookie('jeton-neuf')
      }
      return new Response(null, { status: 204 })
    })

    await requestNoContent('/api/v1/auth/logout', { method: 'POST' })

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/csrf',
      '/api/v1/auth/logout',
    ])
    expect(headersOf(fetchMock.mock.calls[1]).get('X-XSRF-TOKEN')).toBe('jeton-neuf')
  })

  it('AUTH-60 : reloadCsrf lit GET /api/v1/csrf', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 204 }))

    await reloadCsrf()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/csrf')
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('GET')
  })

  it('SEC-01 : une réponse refusée par le schéma Zod devient INTERNAL_ERROR', async () => {
    stubFetch(() => json({ id: 42 }))

    const error = await rejection(request('/api/v1/auth/me', ACCOUNT))

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 200, code: 'INTERNAL_ERROR' })
  })

  it('QA-05 : un Problem Details donne son statut, son code et ses champs, sans le texte du serveur', async () => {
    stubFetch(() =>
      problem(400, {
        status: 400,
        code: 'VALIDATION_FAILED',
        detail: 'texte du serveur',
        fields: ['email'],
      }),
    )

    const error = await rejection(
      requestNoContent('/api/v1/auth/register', { method: 'POST', body: {} }),
    )

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', fields: ['email'] })
    expect(String(error)).not.toContain('texte du serveur')
  })

  it.each([
    [
      'un corps qui n’est pas du JSON',
      () => new Response('<html>Bad gateway</html>', { status: 502 }),
    ],
    ['un Problem Details sans code', () => problem(500, { status: 500, title: 'Erreur' })],
    ['un code inconnu', () => problem(409, { status: 409, code: 'CODE_FUTUR' })],
  ])('QA-05 : %s devient INTERNAL_ERROR, avec le statut reçu', async (_cas, response) => {
    stubFetch(response)

    const error = await rejection(request('/api/v1/auth/me', ACCOUNT))

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('INTERNAL_ERROR')
  })

  it('SEC-10 : un 429 garde le délai de Retry-After, en secondes', async () => {
    stubFetch(() => problem(429, { status: 429, code: 'RATE_LIMITED' }, { 'Retry-After': '61' }))

    const error = await rejection(
      requestNoContent('/api/v1/auth/login', { method: 'POST', body: {} }),
    )

    expect(error).toMatchObject({ status: 429, code: 'RATE_LIMITED', retryAfterSeconds: 61 })
  })

  it.each(['abc', '-5', '1.5', ''])(
    'SEC-10 : un Retry-After illisible (« %s ») ne donne aucun délai',
    async (value) => {
      stubFetch(() => problem(429, { status: 429, code: 'RATE_LIMITED' }, { 'Retry-After': value }))

      const error = await rejection(
        requestNoContent('/api/v1/auth/login', { method: 'POST', body: {} }),
      )

      expect(error).toMatchObject({ code: 'RATE_LIMITED', retryAfterSeconds: null })
    },
  )

  it('A10 : un serveur injoignable donne NetworkError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    )

    const error = await rejection(request('/api/v1/auth/me', ACCOUNT))

    expect(error).toBeInstanceOf(NetworkError)
  })

  it('A10 : une requête annulée reste une annulation, pas une panne réseau', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new DOMException('The operation was aborted.', 'AbortError'))),
    )

    const error = await rejection(request('/api/v1/auth/me', ACCOUNT))

    expect(error).toBeInstanceOf(DOMException)
    expect((error as DOMException).name).toBe('AbortError')
  })

  it('A01 : une adresse hors de /api/v1/ est refusée avant tout appel', async () => {
    const fetchMock = stubFetch(() => json({}))

    await expect(request('https://exemple.ca/api/v1/auth/me', ACCOUNT)).rejects.toThrow()
    await expect(request('/autre', ACCOUNT)).rejects.toThrow()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
