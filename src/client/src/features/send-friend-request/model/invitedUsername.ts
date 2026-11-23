import { isUsernameWellFormed } from '@/entities/account'

/** Champ brut borné comme au serveur (SEC-01, ADR 0037, point 6). */
export const INVITED_USERNAME_MAX_LENGTH = 64

/**
 * ADR 0037 (points 6 et 10) : la même normalisation qu'au serveur. Espaces ASCII autour et `@` initial retirés, tout
 * caractère hors de l'ASCII imprimable refusé, minuscules, puis la forme d'AUTH-01 ; `null` sinon, sans requête.
 */
export function normalizeInvitedUsername(input: string): string | null {
  const trimmed = input.replace(/^ +| +$/gu, '')
  const bare = trimmed.startsWith('@') ? trimmed.slice(1) : trimmed
  if (!/^[\x21-\x7e]*$/u.test(bare)) {
    return null
  }
  const lower = bare.toLowerCase()
  return isUsernameWellFormed(lower) ? lower : null
}
