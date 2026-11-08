import { useLocation, useNavigate } from 'react-router'
import { ErrorPage } from '../layouts'

/**
 * A10 : erreur de rendu d'une route. React Router remet sa frontière à zéro quand l'objet location change :
 * « Réessayer » renavigue vers la même adresse, en remplacement, ce qui refait le rendu sans recharger la page ni
 * perdre l'état en mémoire. Une revalidation ne suffit pas : RouterProvider regroupe ses états dans une transition.
 */
export function RouteErrorBoundary() {
  const navigate = useNavigate()
  const location = useLocation()
  // React Router type l'état d'historique en any : il est seulement retransmis, jamais lu.
  const historyState: unknown = location.state
  return (
    <ErrorPage
      onRetry={() => {
        const { pathname, search, hash } = location
        void navigate({ pathname, search, hash }, { replace: true, state: historyState })
      }}
    />
  )
}
