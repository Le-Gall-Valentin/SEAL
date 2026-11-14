import { Navigate, type RouteObject } from 'react-router'
import { ForgotPasswordPage } from '@/pages/forgot-password'
import { HomePage } from '@/pages/home'
import { LoginPage } from '@/pages/login'
import { RegisterPage } from '@/pages/register'
import { ResetPasswordPage } from '@/pages/reset-password'
import { VerifyEmailPage } from '@/pages/verify-email'
import { ROUTES } from '@/shared/config'
import { RouteErrorBoundary } from './RouteErrorBoundary'
import { AnonymousRoute, PendingRoute } from './routeGuards'

/** Route racine sans chemin : toute erreur de rendu d'une route enfant aboutit à l'état « Erreur de chargement » (A10). */
export function withRootErrorBoundary(children: RouteObject[]): RouteObject[] {
  return [{ ErrorBoundary: RouteErrorBoundary, children }]
}

export const routes = withRootErrorBoundary([
  { path: '/', element: <HomePage /> },
  // AUTH-16 : écrans réservés aux anonymes ; un compte connecté va à l'écran de son état.
  {
    element: <AnonymousRoute />,
    children: [
      { path: ROUTES.LOGIN, element: <LoginPage /> },
      { path: ROUTES.REGISTER, element: <RegisterPage /> },
      { path: ROUTES.FORGOT_PASSWORD, element: <ForgotPasswordPage /> },
    ],
  },
  // ADR 0024, point 9 : sans garde, pour qu'un navigateur connecté se déconnecte sans perdre le lien.
  { path: ROUTES.RESET_PASSWORD, element: <ResetPasswordPage /> },
  // AUTH-16 : la session restreinte d'un compte en attente ne voit que la vérification.
  {
    element: <PendingRoute />,
    children: [{ path: ROUTES.VERIFY_EMAIL, element: <VerifyEmailPage /> }],
  },
  // Aucun écran « page introuvable » dans la maquette : une adresse inconnue ramène à l'accueil (ADR 0012).
  { path: '*', element: <Navigate to="/" replace /> },
])
