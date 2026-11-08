import { Navigate, type RouteObject } from 'react-router'
import { HomePage } from '@/pages/home'
import { RouteErrorBoundary } from './RouteErrorBoundary'

/** Route racine sans chemin : toute erreur de rendu d'une route enfant aboutit à l'état « Erreur de chargement » (A10). */
export function withRootErrorBoundary(children: RouteObject[]): RouteObject[] {
  return [{ ErrorBoundary: RouteErrorBoundary, children }]
}

export const routes = withRootErrorBoundary([
  { path: '/', element: <HomePage /> },
  // Aucun écran « page introuvable » dans la maquette : une adresse inconnue ramène à l'accueil (ADR 0012).
  { path: '*', element: <Navigate to="/" replace /> },
])
