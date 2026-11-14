import { type APIRequestContext, expect, type Page, test } from '@playwright/test'

// Parcours du lot 1 contre SEAL lancé par docker compose ; les courriels sont lus dans Mailpit (compose.yaml).
const MAILPIT = process.env['SEAL_MAILPIT_URL'] ?? 'http://localhost:8025'
const EMAIL = `bout-en-bout.${Date.now().toString(36)}@exemple.ca`
const PASSWORD = 'fjord-tuque-raquette'
const NEW_PASSWORD = 'raquette-sous-la-neige'

interface MailSummary {
  readonly ID: string
  readonly Created: string
  readonly To: readonly { readonly Address: string }[]
}

/** Dernier courriel reçu à l'adresse depuis l'instant donné, dont le texte suit le motif. */
async function mailMatch(
  request: APIRequestContext,
  pattern: RegExp,
  since: number,
): Promise<string> {
  let found: string | undefined
  await expect
    .poll(
      async () => {
        const list = (await (await request.get(`${MAILPIT}/api/v1/messages`)).json()) as {
          messages: MailSummary[]
        }
        for (const message of list.messages) {
          if (
            message.To.some((to) => to.Address === EMAIL) &&
            Date.parse(message.Created) >= since
          ) {
            const full = (await (
              await request.get(`${MAILPIT}/api/v1/message/${message.ID}`)
            ).json()) as { Text: string }
            found = pattern.exec(full.Text)?.[1]
            if (found !== undefined) return true
          }
        }
        return false
      },
      { timeout: 20_000 },
    )
    .toBe(true)
  return found ?? ''
}

async function login(page: Page, password: string) {
  await page.goto('/connexion')
  await page.getByLabel('Identifiant ou courriel').fill(EMAIL)
  await page.getByLabel('Mot de passe', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Se connecter' }).click()
}

test.describe.serial('AUTH-10 à AUTH-70 : parcours d’authentification du lot 1', () => {
  test('AUTH-10, AUTH-14, AUTH-53 : inscription, code reçu, accueil, déconnexion', async ({
    page,
    request,
  }) => {
    const since = Date.now() - 2_000
    await page.goto('/inscription')
    await page.getByLabel('Courriel', { exact: true }).fill(EMAIL)
    await expect(page.getByText('Disponible', { exact: true })).toBeVisible()
    await page.getByLabel('Nom affiché', { exact: true }).fill('Bout en bout')
    await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD)
    await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'Créer mon compte' }).click()

    await expect(page.getByRole('heading', { name: 'Vérifiez votre courriel' })).toBeVisible()
    const code = await mailMatch(request, /code de vérification SEAL : ([0-9]{6})\./, since)
    await page.getByLabel('Code de vérification — 1/6').fill(code)
    await page.getByRole('button', { name: 'Vérifier' }).click()

    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    await page.getByRole('button', { name: 'Se déconnecter' }).click()
    await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  })

  test('AUTH-20, AUTH-21 : connexion, refusée puis acceptée', async ({ page }) => {
    await login(page, `${PASSWORD}!`)
    await expect(page.getByRole('alert')).toHaveText('Identifiant ou mot de passe incorrect.')

    await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'Se connecter' }).click()
    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    expect(new URL(page.url()).pathname).toBe('/')
  })

  test('AUTH-70 : mot de passe oublié, lien reçu, nouveau mot de passe, connexion avec lui', async ({
    page,
    request,
  }) => {
    const since = Date.now() - 2_000
    await page.goto('/mot-de-passe/oublie')
    await page.getByLabel('Courriel', { exact: true }).fill(EMAIL)
    await page.getByRole('button', { name: 'Envoyer le lien' }).click()
    await expect(page.getByRole('status')).toContainText('Vérifiez votre boîte de réception')

    const link = await mailMatch(
      request,
      /(https:\/\/[^\s]+\/mot-de-passe\/nouveau#jeton=[A-Za-z0-9_-]{43})/,
      since,
    )
    await page.goto(link)
    // ADR 0022 : le jeton quitte l'adresse dès l'ouverture.
    // AUTH-04 : l'identifiant suggéré pour « bout-en-bout.… » est « bout.en.bout.… ».
    await expect(
      page.getByText(/^Pour @bout\.en\.bout\..*\. Ce lien expire dans \d+ minutes\.$/),
    ).toBeVisible()
    expect(page.url()).not.toContain('jeton')
    await page.getByLabel('Nouveau mot de passe', { exact: true }).fill(NEW_PASSWORD)
    await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill(NEW_PASSWORD)
    await page.getByRole('button', { name: 'Enregistrer le mot de passe' }).click()
    await expect(page.getByRole('status')).toHaveText(
      'Mot de passe enregistré. Connectez-vous avec le nouveau.',
    )

    await login(page, NEW_PASSWORD)
    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  })
})
