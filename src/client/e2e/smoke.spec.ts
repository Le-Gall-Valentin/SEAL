import { expect, test } from '@playwright/test'

// Valeur exacte de la section 12.3 du CDC.
const CSP =
  "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; " +
  "font-src 'self'; connect-src 'self' wss://localhost:8443; manifest-src 'self'; " +
  "base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'"

// Chromium journalise en erreur toute réponse 401 ; celle de /me est l'état « anonyme » d'AUTH-16, rien d'autre.
const NETWORK_401 = 'Failed to load resource: the server responded with a status of 401 ()'

test('TRN-20, TRN-21 : le client est servi en HTTPS sous la CSP stricte, sans violation ni ressource tierce', async ({
  page,
  baseURL,
}) => {
  const errors: string[] = []
  const requests: string[] = []
  const unauthorized: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('request', (request) => requests.push(request.url()))
  page.on('response', (response) => {
    if (response.status() === 401) unauthorized.push(new URL(response.url()).pathname)
  })

  const response = await page.goto('/')

  expect(response?.status()).toBe(200)
  expect(response?.headers()['content-security-policy']).toBe(CSP)
  expect(response?.headers()['strict-transport-security']).toBe('max-age=300')
  await expect(page).toHaveTitle('SEAL')
  // AUTH-16 : un anonyme est mené à la connexion.
  await expect(page.getByRole('heading', { level: 1, name: 'Connexion' })).toBeVisible()
  expect(page.url()).toBe(`${baseURL ?? ''}/connexion`)
  expect(unauthorized.every((path) => path === '/api/v1/auth/me')).toBe(true)
  expect(errors.filter((error) => error !== NETWORK_401)).toEqual([])
  expect(errors.filter((error) => error === NETWORK_401).length).toBeLessThanOrEqual(
    unauthorized.length,
  )
  expect(requests.filter((url) => !url.startsWith(`${baseURL ?? ''}/`))).toEqual([])
})
