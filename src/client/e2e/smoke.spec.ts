import { expect, test } from '@playwright/test'

// Valeur exacte de la section 12.3 du CDC.
const CSP =
  "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; " +
  "font-src 'self'; connect-src 'self' wss://localhost:8443; manifest-src 'self'; " +
  "base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'"

test('TRN-20, TRN-21 : le client est servi en HTTPS sous la CSP stricte, sans violation ni ressource tierce', async ({
  page,
  baseURL,
}) => {
  const errors: string[] = []
  const requests: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('request', (request) => requests.push(request.url()))

  const response = await page.goto('/')

  expect(response?.status()).toBe(200)
  expect(response?.headers()['content-security-policy']).toBe(CSP)
  expect(response?.headers()['strict-transport-security']).toBe('max-age=300')
  await expect(page).toHaveTitle('SEAL')
  await expect(page.getByRole('heading', { name: 'SEAL' })).toBeVisible()
  expect(errors).toEqual([])
  expect(requests.filter((url) => !url.startsWith(`${baseURL ?? ''}/`))).toEqual([])
})
