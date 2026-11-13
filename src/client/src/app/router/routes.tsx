import { Navigate, type RouteObject } from 'react-router'
import { HomePage } from '@/pages/home'
import { LoginPage } from '@/pages/login'
import { ROUTES } from '@/shared/config'
import { RouteErrorBoundary } from './RouteErrorBoundary'
import { AnonymousRoute } from './routeGuards'

/** Route racine sans chemin : toute erreur de rendu d'une route enfant aboutit à l'état « Erreur de chargement » (A10). */
export function withRootErrorBoundary(children: RouteObject[]): RouteObject[] {
  return [{ ErrorBoundary: RouteErrorBoundary, children }]
}

export const routes = withRootErrorBoundary([
  { path: '/', element: <HomePage /> },
  // AUTH-16 : écrans réservés aux anonymes ; un compte connecté va à l'écran de son état.
  {
    element: <AnonymousRoute />,
    children: [{ path: ROUTES.LOGIN, element: <LoginPage /> }],
  },
  // Aucun écran « page introuvable » dans la maquette : une adresse inconnue ramène à l'accueil (ADR 0012).
  { path: '*', element: <Navigate to="/" replace /> },
])
