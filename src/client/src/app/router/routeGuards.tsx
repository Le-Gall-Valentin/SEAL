import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router'
import {
  destinationFor,
  type SessionState,
  sessionStateOf,
  useCurrentAccount,
} from '@/entities/session'
import { ErrorPage } from '../layouts'

/**
 * AUTH-16 : chaque écran n'accepte qu'un état du compte ; un autre état mène à son propre écran (destinationFor).
 * Rien ne s'affiche avant la réponse de /me ; si sa première lecture échoue, « Réessayer » le relit (A10).
 */
function useSessionGate(allow: SessionState): ReactNode {
  const account = useCurrentAccount()
  if (account.isPending) {
    return null
  }
  // A10 : seule une première lecture ratée remplace l'écran ; une relecture ratée (retour du focus) garde le compte
  // connu, l'écran et sa saisie.
  if (account.isError && account.data === undefined) {
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

/** Écrans réservés aux anonymes : connexion, inscription, mot de passe oublié. */
export function AnonymousRoute() {
  return useSessionGate('anonymous')
}

/** Écran du compte en attente de vérification (session restreinte). */
export function PendingRoute() {
  return useSessionGate('pending')
}

/** Écrans du compte actif. */
export function ActiveRoute() {
  return useSessionGate('active')
}
