import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { LanguageProvider } from '@/shared/i18n'
import { ErrorBoundary, routerErrorHandler } from './providers'
import { routes } from './router'

const router = createBrowserRouter(routes)

export function App() {
  return (
    // ACC-05 : le français, seule langue tant que la préférence de langue n'est pas livrée (ADR 0017).
    <LanguageProvider language="fr">
      <ErrorBoundary>
        <RouterProvider router={router} onError={routerErrorHandler(import.meta.env.PROD)} />
      </ErrorBoundary>
    </LanguageProvider>
  )
}
