import { Navigate, Outlet } from 'react-router'
import {
  destinationFor,
  type SessionState,
  sessionStateOf,
  useCurrentAccount,
} from '@/entities/session'
import { ErrorPage } from '../layouts'

interface SessionGuardProps {
  /** État du compte que les routes enfants acceptent (AUTH-16). */
  readonly allow: SessionState
}

/**
 * AUTH-16 : chaque écran n'accepte qu'un état du compte ; un autre état mène à son propre écran (destinationFor).
 * Rien ne s'affiche avant la réponse de /me ; si /me échoue, « Réessayer » le relit (A10).
 */
export function SessionGuard({ allow }: SessionGuardProps) {
  const account = useCurrentAccount()
  if (account.isPending) {
    return null
  }
  if (account.isError) {
    return (
      <ErrorPage
        onRetry={() => {
          void account.refetch()
        }}
      />
    )
  }
  const state = sessionStateOf(account.data)
  return state === allow ? <Outlet /> : <Navigate to={destinationFor(state)} replace />
}
