import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { PasswordBlocklistProvider, passwordBlocklist } from '@/entities/password'
import { SessionApiProvider, sessionApi } from '@/entities/session'
import { LoginApiProvider, loginApi } from '@/features/login'
import { RegisterApiProvider, registerApi } from '@/features/register'
import { VerifyEmailApiProvider, verifyEmailApi } from '@/features/verify-email'
import { LanguageProvider } from '@/shared/i18n'
import { createQueryClient, ErrorBoundary, routerErrorHandler } from './providers'
import { routes } from './router'

const router = createBrowserRouter(routes)
const queryClient = createQueryClient()

// AUTH-60 : jeton CSRF rechargé au démarrage. Un échec est sans suite : la première requête non sûre le recharge.
sessionApi.reloadCsrf().catch(() => undefined)

/** Racine de composition (ADR 0024, point 7) : seul endroit qui associe chaque port à son adaptateur. */
export function App() {
  return (
    // ACC-05 : le français, seule langue tant que la préférence de langue n'est pas livrée (ADR 0017).
    <LanguageProvider language="fr">
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <SessionApiProvider api={sessionApi}>
            <PasswordBlocklistProvider api={passwordBlocklist}>
              <LoginApiProvider api={loginApi}>
                <RegisterApiProvider api={registerApi}>
                  <VerifyEmailApiProvider api={verifyEmailApi}>
                    <RouterProvider
                      router={router}
                      onError={routerErrorHandler(import.meta.env.PROD)}
                    />
                  </VerifyEmailApiProvider>
                </RegisterApiProvider>
              </LoginApiProvider>
            </PasswordBlocklistProvider>
          </SessionApiProvider>
        </QueryClientProvider>
      </ErrorBoundary>
    </LanguageProvider>
  )
}
