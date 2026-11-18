import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router'
import { useAuthorityCheck } from '@/entities/authority'
import {
  destinationFor,
  type SessionState,
  sessionStateOf,
  useCurrentAccount,
} from '@/entities/session'
import { AuthorityChangedPage } from '@/pages/authority-changed'
import { ErrorPage } from '../layouts'

/**
 * AUTH-16 : chaque écran n'accepte qu'un état du compte ; un autre état mène à son propre écran (destinationFor).
 * Rien ne s'affiche avant la réponse de /me ; si sa première lecture échoue, « Réessayer » le relit (A10).
 */
function useSessionGate(allow: SessionState, allowed: ReactNode = <Outlet />): ReactNode {
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
  return state === allow ? allowed : <Navigate to={destinationFor(state)} replace />
}

/**
 * KEY-11, ADR 0027 (point 5) : la zone du compte actif n'affiche rien avant le contrôle de l'Autorité ; une clé changée
 * ou une réponse incohérente bloque l'application. GET /pki/ca exige une session complète : seul un compte actif
 * l'interroge. Comme pour /me, seule une première lecture ratée remplace l'écran (A10).
 */
function AuthorityGate() {
  const check = useAuthorityCheck()
  if (check.data === 'changed' || check.data === 'invalid') {
    return <AuthorityChangedPage />
  }
  if (check.data === undefined) {
    return check.isError ? (
      <ErrorPage
        onRetry={() => {
          void check.refetch()
        }}
      />
    ) : null
  }
  return <Outlet />
}

/** Écrans réservés aux anonymes : connexion, inscription, mot de passe oublié. */
export function AnonymousRoute() {
  return useSessionGate('anonymous')
}

/** Écran du compte en attente de vérification (session restreinte). */
export function PendingRoute() {
  return useSessionGate('pending')
}

/** Écrans du compte actif, après le contrôle de l'Autorité (KEY-11). */
export function ActiveRoute() {
  return useSessionGate('active', <AuthorityGate />)
}
