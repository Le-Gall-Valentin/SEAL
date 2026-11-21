import { expect, test } from '@playwright/test'

// Écrans OAuth (ADR 0033) contre SEAL lancé par docker compose. Le parcours réel avec Google et GitHub se teste à la
// main, avec les applications de démonstration (ADR 0033, point 4) : ces tests ne supposent aucun fournisseur.

const PROVIDER_NAMES: Readonly<Record<string, string>> = { google: 'Google', github: 'GitHub' }

test('OPS-03, AUTH-35 : Connexion et Inscription montrent exactement les fournisseurs configurés', async ({
  page,
}) => {
  for (const path of ['/connexion', '/inscription']) {
    // La liste lue par l'écran lui-même : ce test vaut avec ou sans fournisseur configuré dans .env.
    const [response] = await Promise.all([
      page.waitForResponse(
        (candidate) => new URL(candidate.url()).pathname === '/api/v1/auth/providers',
      ),
      page.goto(path),
    ])
    expect(response.status()).toBe(200)
    const { providers } = (await response.json()) as { providers: string[] }
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    const buttons = page.getByRole('link', { name: /^Continuer avec / })
    await expect(buttons).toHaveCount(providers.length)
    for (const [index, provider] of providers.entries()) {
      await expect(buttons.nth(index)).toHaveAccessibleName(
        `Continuer avec ${PROVIDER_NAMES[provider] ?? provider}`,
      )
      await expect(buttons.nth(index)).toHaveAttribute('href', `/oauth2/authorization/${provider}`)
    }
    // ADR 0024 (point 3) : sans fournisseur, pas de séparateur « ou ».
    await expect(page.getByText('ou', { exact: true })).toHaveCount(providers.length === 0 ? 0 : 1)
  }
})

test('ADR 0033 (point 2) : Identifiant-OAuth sans inscription en attente ramène à la connexion', async ({
  page,
  baseURL,
}) => {
  await page.goto('/inscription/identifiant')

  await expect(page.getByRole('alert')).toHaveText('La connexion n’a pas aboutiRéessayez.')
  // ADR 0033 (point 1) : lu, le code quitte l'adresse.
  await expect(page).toHaveURL(`${baseURL ?? ''}/connexion`)
})

test('ADR 0032 (point 2) : un retour OAuth refusé ramène à la connexion avec l’alerte, sans session', async ({
  page,
  baseURL,
}) => {
  // AUTH-16 : l'écran relit /me ; un retour refusé n'a ouvert aucune session.
  const [me] = await Promise.all([
    page.waitForResponse((candidate) => new URL(candidate.url()).pathname === '/api/v1/auth/me'),
    page.goto('/login/oauth2/code/google?code=inconnu&state=inconnu'),
  ])

  expect(me.status()).toBe(401)
  await expect(page.getByRole('heading', { level: 1, name: 'Connexion' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveText('La connexion n’a pas aboutiRéessayez.')
  await expect(page).toHaveURL(`${baseURL ?? ''}/connexion`)
})
