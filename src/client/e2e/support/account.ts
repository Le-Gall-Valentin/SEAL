import { readFile } from 'node:fs/promises'
import { type APIRequestContext, expect, type Page } from '@playwright/test'

// Aides des parcours de bout en bout contre SEAL lancé par docker compose ; les courriels sont lus dans Mailpit
// (compose.yaml).
const MAILPIT = process.env['SEAL_MAILPIT_URL'] ?? 'http://localhost:8025'

interface MailSummary {
  readonly ID: string
  readonly Created: string
  readonly To: readonly { readonly Address: string }[]
}

/** Dernier courriel reçu à l'adresse depuis l'instant donné, dont le texte suit le motif. */
export async function mailMatch(
  request: APIRequestContext,
  to: string,
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
            message.To.some((recipient) => recipient.Address === to) &&
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
export async function confirmRecoveryKey(page: Page): Promise<string> {
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

/**
 * AUTH-10, AUTH-14, KEY-02 : inscription, code reçu, clé de récupération ; le compte arrive à l'accueil. Rend la clé de
 * récupération.
 */
export async function registerAccount(
  page: Page,
  request: APIRequestContext,
  account: { readonly email: string; readonly displayName: string; readonly password: string },
): Promise<string> {
  const since = Date.now() - 2_000
  await page.goto('/inscription')
  await page.getByLabel('Courriel', { exact: true }).fill(account.email)
  await expect(page.getByText('Disponible', { exact: true })).toBeVisible()
  await page.getByLabel('Nom affiché', { exact: true }).fill(account.displayName)
  await page.getByLabel('Mot de passe', { exact: true }).fill(account.password)
  await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: 'Créer mon compte' }).click()

  await expect(page.getByRole('heading', { name: 'Vérifiez votre courriel' })).toBeVisible()
  const code = await mailMatch(
    request,
    account.email,
    /code de vérification SEAL : ([0-9]{6})\./,
    since,
  )
  await page.getByLabel('Code de vérification — 1/6').fill(code)
  await page.getByRole('button', { name: 'Vérifier' }).click()

  const recoveryKey = await confirmRecoveryKey(page)
  await expect(page.getByRole('dialog')).toBeHidden()
  await expect(page.getByRole('heading', { level: 1, name: 'Conversations' })).toBeVisible()
  return recoveryKey
}
