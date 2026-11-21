import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router'
import { useAuthorityCheck } from '@/entities/authority'
import { type KeyOwner, useKeyStatus } from '@/entities/keyring'
import {
  destinationFor,
  type SessionState,
  sessionStateOf,
  useCurrentAccount,
} from '@/entities/session'
import { KeySetupDialog } from '@/features/key-setup'
import { AuthorityChangedPage } from '@/pages/authority-changed'
import { IdentityChangedPage } from '@/pages/identity-changed'
import { RestoreKeysPage } from '@/pages/restore-keys'
import { LogoutButton } from '@/features/logout'
import { KeysWaitingPage } from '@/pages/keys-waiting'
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
  return <KeyGate />
}

/**
 * KEY-02, KEY-33, ADR 0029 (point 3) : après l'Autorité, la zone du compte attend un trousseau complet. Première
 * connexion : le dialogue de la clé, seul sur le fond ; nouvel appareil : la restauration ; certificat d'une autre IK :
 * blocage ; verrou tenu par un autre onglet : attente (point 13). Rien du compte ne s'affiche avant « ready ». Un état
 * illisible mène à « Impossible de charger », avec « Se déconnecter » (point 10) ; une relecture ratée en
 * arrière-plan garde l'écran affiché et sa saisie (A10).
 */
function KeyGate() {
  const account = useCurrentAccount().data
  if (account === undefined || account === null) {
    return null
  }
  return <KeyGateFor owner={{ userId: account.id, username: account.username }} />
}

function KeyGateFor({ owner }: { readonly owner: KeyOwner }) {
  const keys = useKeyStatus(owner)
  if (keys.failed) {
    return <ErrorPage onRetry={keys.reread} extra={<LogoutButton variant="outlined" />} />
  }
  switch (keys.status) {
    case undefined:
      return keys.waiting ? <KeysWaitingPage /> : null
    case 'ready':
      return <Outlet />
    case 'setup':
      return <KeySetupDialog owner={owner} onDone={keys.reread} />
    case 'restore':
      return <RestoreKeysPage owner={owner} onRestored={keys.reread} />
    case 'foreign':
      return <IdentityChangedPage />
  }
}

/** Écrans réservés aux anonymes : connexion, inscription, Identifiant-OAuth, mot de passe oublié. */
export function AnonymousRoute() {
  return useSessionGate('anonymous')
}

/** Écran du compte en attente de vérification (session restreinte). */
export function PendingRoute() {
  return useSessionGate('pending')
}

/** Écrans du compte actif, après le contrôle de l'Autorité (KEY-11) et un trousseau complet (KEY-02, KEY-33). */
export function ActiveRoute() {
  return useSessionGate('active', <AuthorityGate />)
}
