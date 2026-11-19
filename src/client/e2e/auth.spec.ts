import { readFile } from 'node:fs/promises'
import { type APIRequestContext, expect, type Page, test } from '@playwright/test'

// Parcours du lot 1 contre SEAL lancé par docker compose ; les courriels sont lus dans Mailpit (compose.yaml).
const MAILPIT = process.env['SEAL_MAILPIT_URL'] ?? 'http://localhost:8025'
const EMAIL = `bout-en-bout.${Date.now().toString(36)}@exemple.ca`
const PASSWORD = 'fjord-tuque-raquette'
const NEW_PASSWORD = 'raquette-sous-la-neige'
/** Clé de récupération notée à l'inscription (KEY-30), ressaisie sur chaque nouvel appareil (KEY-33). */
let recoveryKey = ''

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

/**
 * KEY-02, KEY-30 : Dialogue-Cle-Recuperation. Relit les 8 groupes, télécharge le .txt, ressaisit les deux groupes
 * demandés, puis « Terminer ». Rend la clé, groupes séparés par des espaces.
 */
async function confirmRecoveryKey(page: Page): Promise<string> {
  await expect(page.getByRole('dialog', { name: 'Votre clé de récupération' })).toBeVisible()
  const groups = await page
    .getByRole('list', { name: 'Clé de récupération, 8 groupes de 4 caractères' })
    .getByRole('listitem')
    .evaluateAll((items) => items.map((item) => item.lastElementChild?.textContent ?? ''))
  expect(groups).toHaveLength(8)

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Télécharger (.txt)' }).click()
  const file = await download
  expect(file.suggestedFilename()).toMatch(/^seal-cle-de-recuperation-[a-z0-9._]+\.txt$/)
  const text = await readFile(await file.path(), 'utf-8')
  expect(text).toContain(`${groups.slice(0, 4).join(' ')}\n${groups.slice(4).join(' ')}`)

  await page.getByRole('button', { name: 'J’ai noté ma clé' }).click()
  const labels = await page
    .locator('label')
    .filter({ hasText: /^Groupe [1-8]$/ })
    .allTextContents()
  expect(labels).toHaveLength(2)
  for (const label of labels) {
    await page.getByLabel(label, { exact: true }).fill(groups[Number(label.slice(-1)) - 1] ?? '')
  }
  await expect(page.getByRole('status')).toHaveText('Clé confirmée.')
  await page.getByRole('button', { name: 'Terminer' }).click()
  return groups.join(' ')
}

/** KEY-33 : Restauration-Appareil ; la clé collée dans la première case se répartit dans les huit. */
async function restore(page: Page, key: string) {
  await expect(page.getByRole('heading', { name: 'Restaurer vos clés' })).toBeVisible()
  await page.getByLabel('Groupe 1 sur 8').fill(key)
  await page.getByRole('button', { name: 'Restaurer mes clés' }).click()
}

/**
 * KEY-33 : la restauration a abouti. « Se déconnecter » ne le prouve pas, Restauration-Appareil l'affiche aussi à la
 * même adresse : son titre doit disparaître, et l'accueil (logo « seal ») s'afficher.
 */
async function expectRestored(page: Page) {
  await expect(page.getByRole('heading', { name: 'Restaurer vos clés' })).toBeHidden()
  await expect(page.getByRole('heading', { level: 1, name: 'seal' })).toBeVisible()
  expect(new URL(page.url()).pathname).toBe('/')
}

async function login(page: Page, password: string) {
  await page.goto('/connexion')
  await page.getByLabel('Identifiant ou courriel').fill(EMAIL)
  await page.getByLabel('Mot de passe', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Se connecter' }).click()
}

test.describe.serial('AUTH-10 à AUTH-70 : parcours d’authentification du lot 1', () => {
  test('AUTH-10, AUTH-14, AUTH-53, KEY-02 : inscription, code reçu, clé de récupération, accueil, déconnexion', async ({
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

    recoveryKey = await confirmRecoveryKey(page)
    await expect(page.getByRole('dialog')).toBeHidden()
    await expect(page.getByRole('heading', { level: 1, name: 'seal' })).toBeVisible()
    await page.getByRole('button', { name: 'Se déconnecter' }).click()
    await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  })

  test('AUTH-20, AUTH-21, KEY-33 : connexion refusée puis acceptée, restauration sur un nouvel appareil', async ({
    page,
  }) => {
    await login(page, `${PASSWORD}!`)
    await expect(page.getByRole('alert')).toHaveText('Identifiant ou mot de passe incorrect.')

    await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'Se connecter' }).click()

    // KEY-33 : nouveau contexte, donc nouvel appareil ; une clé fausse est refusée, la bonne restaure le trousseau.
    await restore(page, '0000 1111 2222 3333 4444 5555 6666 7777')
    await expect(page.getByRole('alert')).toHaveText(
      'Cette clé de récupération ne correspond pas à votre coffre.',
    )
    await restore(page, recoveryKey)
    await expectRestored(page)
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
    await restore(page, recoveryKey)
    await expectRestored(page)
  })
})
