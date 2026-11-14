/** Codes stables des Problem Details (CDC, section 14.6) ; le client traduit le code, jamais le texte du serveur. */
export const ERROR_CODES = [
  'VALIDATION_FAILED',
  'UNAUTHENTICATED',
  'MFA_REQUIRED',
  'REAUTH_REQUIRED',
  'FORBIDDEN',
  'NOT_FOUND',
  'RATE_LIMITED',
  'REQUEST_REJECTED',
  'INTERNAL_ERROR',
  'USERNAME_TAKEN',
  'USERNAME_INVALID',
  'USERNAME_RESERVED',
  'PASSWORD_TOO_WEAK',
  'PASSWORD_COMPROMISED',
  'INVALID_CODE',
  'CODE_EXPIRED',
  'INVALID_CREDENTIALS',
  'LAST_LOGIN_METHOD',
  'OAUTH_EMAIL_IN_USE',
  'OAUTH_ALREADY_LINKED',
  'USER_NOT_FOUND',
  'CANNOT_INVITE_SELF',
  'ALREADY_FRIENDS',
  'REQUEST_ALREADY_PENDING',
  'INCOMING_REQUEST_EXISTS',
  'CANNOT_INVITE',
  'NOT_FRIENDS',
  'NOT_A_MEMBER',
  'GROUP_FULL',
  'STALE_GROUP_STATE',
  'KEY_NOT_FOUND',
  'INVALID_SIGNATURE',
  'CERTIFICATE_REVOKED',
  'DUPLICATE_MESSAGE',
  'RECIPIENTS_MISMATCH',
  'VAULT_VERSION_CONFLICT',
] as const

export type ErrorCode = (typeof ERROR_CODES)[number]

const KNOWN: ReadonlySet<string> = new Set(ERROR_CODES)

function isErrorCode(value: string): value is ErrorCode {
  return KNOWN.has(value)
}

/** Un code inconnu, d'une version plus récente du serveur par exemple, s'affiche comme une erreur interne. */
export function toErrorCode(value: string): ErrorCode {
  return isErrorCode(value) ? value : 'INTERNAL_ERROR'
}

interface ApiErrorDetails {
  /** Champs en erreur d'un VALIDATION_FAILED, sans leur valeur (ADR 0010). */
  readonly fields?: readonly string[]
  /** Délai d'un RATE_LIMITED, en secondes (SEC-10). */
  readonly retryAfterSeconds?: number | null
}

/** Réponse d'erreur du serveur. Son message est son code : le texte du serveur n'est ni gardé ni affiché. */
export class ApiError extends Error {
  readonly status: number
  readonly code: ErrorCode
  readonly fields: readonly string[]
  readonly retryAfterSeconds: number | null

  constructor(status: number, code: ErrorCode, details: ApiErrorDetails = {}) {
    super(code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = details.fields ?? []
    this.retryAfterSeconds = details.retryAfterSeconds ?? null
  }
}

/** Serveur injoignable : aucune réponse reçue. */
export class NetworkError extends Error {
  constructor() {
    super('NETWORK')
    this.name = 'NetworkError'
  }
}

/** AUTH-53 : la session n'existe plus (expirée, révoquée, ou version périmée de l'ADR 0023). */
export function isUnauthenticated(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'UNAUTHENTICATED'
}

/** ADR 0015 : « Réessayez dans N minutes », N venant de Retry-After arrondi à la minute supérieure. */
export function retryAfterMinutes(seconds: number): number {
  return Math.max(1, Math.ceil(seconds / 60))
}
