import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App, rootErrorOptions } from '@/app'

const container = document.getElementById('root')
if (container === null) {
  throw new Error('Élément #root introuvable dans index.html')
}

createRoot(container, rootErrorOptions(import.meta.env.PROD)).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
