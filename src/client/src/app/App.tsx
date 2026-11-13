import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { LanguageProvider } from '@/shared/i18n'
import { createQueryClient, ErrorBoundary, routerErrorHandler } from './providers'
import { routes } from './router'

const router = createBrowserRouter(routes)
const queryClient = createQueryClient()

export function App() {
  return (
    // ACC-05 : le français, seule langue tant que la préférence de langue n'est pas livrée (ADR 0017).
    <LanguageProvider language="fr">
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} onError={routerErrorHandler(import.meta.env.PROD)} />
        </QueryClientProvider>
      </ErrorBoundary>
    </LanguageProvider>
  )
}
