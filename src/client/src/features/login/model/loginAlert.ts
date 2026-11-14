import { z } from 'zod'
import { PASSWORD_INPUT_MAX_LENGTH } from '@/entities/password'
import { failureOf } from '@/shared/lib'
import type { Credentials } from '../api/ILoginApi'

/** Alerte du formulaire de connexion (Connexion-Tablette, Connexion-Telephone, ADR 0024, point 8). */
export type LoginAlert =
  | { readonly kind: 'invalid-credentials' }
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'network' }
  | { readonly kind: 'unexpected' }

export function loginAlertOf(error: unknown): LoginAlert {
  const failure = failureOf(error)
  if (failure.kind !== 'refused') {
    return failure
  }
  // AUTH-21 : tout échec de l'identification répond INVALID_CREDENTIALS ; un autre refus n'est pas attendu.
  return failure.code === 'INVALID_CREDENTIALS'
    ? { kind: 'invalid-credentials' }
    : { kind: 'unexpected' }
}

/** SEC-01 : bornes de LoginRequest côté serveur (ADR 0021, point 3). */
const CREDENTIALS = z.object({
  login: z.string().trim().min(1).max(254),
  password: z.string().min(1).max(PASSWORD_INPUT_MAX_LENGTH),
})

export function canSubmit(credentials: Credentials): boolean {
  return CREDENTIALS.safeParse(credentials).success
}
