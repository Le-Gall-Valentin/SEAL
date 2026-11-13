import { ROUTES } from '@/shared/config'
import type { Account } from '../api/ISessionApi'

export const SESSION_STATES = ['anonymous', 'pending', 'active'] as const

/** AUTH-16 : anonyme, compte en attente de vérification (session restreinte) ou compte actif. */
export type SessionState = (typeof SESSION_STATES)[number]

export function sessionStateOf(account: Account | null): SessionState {
  if (account === null) {
    return 'anonymous'
  }
  return account.status === 'PENDING_EMAIL' ? 'pending' : 'active'
}

/** Écran de chaque état ; chacun accepte l'état qui y mène, si bien qu'aucune redirection ne boucle. */
export function destinationFor(state: SessionState): string {
  switch (state) {
    case 'anonymous':
      return ROUTES.LOGIN
    case 'pending':
      return ROUTES.VERIFY_EMAIL
    case 'active':
      return ROUTES.HOME
  }
}
