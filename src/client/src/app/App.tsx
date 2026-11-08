import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { ErrorBoundary, routerErrorHandler } from './providers'
import { routes } from './router'

const router = createBrowserRouter(routes)

export function App() {
  return (
    <ErrorBoundary>
      <RouterProvider router={router} onError={routerErrorHandler(import.meta.env.PROD)} />
    </ErrorBoundary>
  )
}
