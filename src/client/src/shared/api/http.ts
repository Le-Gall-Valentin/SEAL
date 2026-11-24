import { z } from 'zod'
import { ApiError, NetworkError, toErrorCode } from '@/shared/lib'

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  readonly method?: Method
  /** Corps envoyé en JSON. */
  readonly body?: unknown
  readonly signal?: AbortSignal
}

const API_PREFIX = '/api/v1/'
const CSRF_PATH = '/api/v1/csrf'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

/** RFC 9457 et ADR 0010 : seuls le code et les noms des champs servent ; le texte du serveur est ignoré. */
const PROBLEM = z.object({
  code: z.string().max(64),
  fields: z.array(z.string().max(200)).max(100).optional(),
  requestId: z.string().max(64).optional(),
})

/** UUID textuel, comme les identifiants du serveur. */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

/**
 * FRD-01, ADR 0038 (points 8 et 9) : un requestId est gardé s'il est un UUID ; la feature d'invitation ne s'en sert
 * que pour INCOMING_REQUEST_EXISTS.
 */
function requestIdOf(requestId: string | undefined): string | null {
  return requestId !== undefined && UUID_PATTERN.test(requestId) ? requestId : null
}

/** A01, A05 : le chemin reste sous /api/v1/ ; un segment « . » ou « .. » le déplacerait une fois résolu. */
function isApiPath(path: string): boolean {
  const pathname = path.split(/[?#]/, 1)[0] ?? ''
  return (
    pathname.startsWith(API_PREFIX) &&
    !pathname.split('/').some((segment) => segment === '.' || segment === '..')
  )
}

function csrfToken(): string | null {
  for (const cookie of document.cookie.split(';')) {
    const [name, ...value] = cookie.trim().split('=')
    if (name === CSRF_COOKIE && value.length > 0) {
      const token = value.join('=')
      return token === '' ? null : token
    }
  }
  return null
}

/** SEC-10 : Retry-After en secondes entières ; toute autre forme est ignorée. */
function retryAfterSeconds(response: Response): number | null {
  const value = response.headers.get('Retry-After')
  return value !== null && /^\d{1,9}$/.test(value) ? Number(value) : null
}

async function problemOf(response: Response): Promise<ApiError> {
  const body: unknown = await response.json().catch(() => null)
  const parsed = PROBLEM.safeParse(body)
  if (!parsed.success) {
    return new ApiError(response.status, 'INTERNAL_ERROR')
  }
  return new ApiError(response.status, toErrorCode(parsed.data.code), {
    fields: parsed.data.fields ?? [],
    retryAfterSeconds: retryAfterSeconds(response),
    requestId: requestIdOf(parsed.data.requestId),
  })
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  if (!isApiPath(path)) {
    throw new Error(`Adresse hors de l'API : ${path}`)
  }
  const method = options.method ?? 'GET'
  const headers = new Headers({ Accept: 'application/json' })
  if (method !== 'GET') {
    // AUTH-60 : un jeton absent (expiré, ou retiré à l'ouverture d'une session) est rechargé avant l'envoi.
    if (csrfToken() === null && path !== CSRF_PATH) {
      await reloadCsrf()
    }
    const token = csrfToken()
    if (token !== null) {
      headers.set(CSRF_HEADER, token)
    }
  }
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? null : JSON.stringify(options.body),
      signal: options.signal ?? null,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new NetworkError()
  }
  if (!response.ok) {
    throw await problemOf(response)
  }
  return response
}

/** Requête dont la réponse JSON est validée par son schéma (SEC-01) ; une réponse refusée est une erreur interne. */
export async function request<T>(
  path: string,
  schema: z.ZodType<T>,
  options: RequestOptions = {},
): Promise<T> {
  const response = await send(path, options)
  const body: unknown = await response.json().catch(() => undefined)
  const parsed = schema.safeParse(body)
  if (!parsed.success) {
    throw new ApiError(response.status, 'INTERNAL_ERROR')
  }
  return parsed.data
}

/** Requête sans contenu en retour (204). */
export async function requestNoContent(path: string, options: RequestOptions = {}): Promise<void> {
  await send(path, options)
}

/** AUTH-60 : dépose un jeton CSRF neuf dans le cookie XSRF-TOKEN. */
export async function reloadCsrf(): Promise<void> {
  await send(CSRF_PATH, { method: 'GET' })
}
