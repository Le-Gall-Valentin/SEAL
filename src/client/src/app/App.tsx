import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { passwordBlocklist } from '@/entities/password'
import { sessionApi } from '@/entities/session'
import { loginApi } from '@/features/login'
import { logoutApi } from '@/features/logout'
import { passwordResetApi } from '@/features/password-reset'
import { registerApi } from '@/features/register'
import { verifyEmailApi } from '@/features/verify-email'
import { LanguageProvider } from '@/shared/i18n'
import {
  ApiProviders,
  type Apis,
  bootstrap,
  createQueryClient,
  ErrorBoundary,
  routerErrorHandler,
} from './providers'
import { routes } from './router'

const router = createBrowserRouter(routes)
const queryClient = createQueryClient()

/** Racine de composition (ADR 0024, point 7) : seul endroit qui associe chaque port à son adaptateur. */
const apis: Apis = {
  session: sessionApi,
  passwordBlocklist,
  login: loginApi,
  register: registerApi,
  verifyEmail: verifyEmailApi,
  passwordReset: passwordResetApi,
  logout: logoutApi,
}

bootstrap(apis)

export function App() {
  return (
    // ACC-05 : le français, seule langue tant que la préférence de langue n'est pas livrée (ADR 0017).
    <LanguageProvider language="fr">
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <ApiProviders apis={apis}>
            <RouterProvider router={router} onError={routerErrorHandler(import.meta.env.PROD)} />
          </ApiProviders>
        </QueryClientProvider>
      </ErrorBoundary>
    </LanguageProvider>
  )
}
