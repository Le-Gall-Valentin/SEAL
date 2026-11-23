import { expect, type Page, test } from '@playwright/test'
import { confirmRecoveryKey, registerAccount } from './support/account'

// KEY-34, ADR 0036 contre SEAL lancé par docker compose. Chaque test a son contexte, donc son appareil : IndexedDB y
// est vide. La connexion qui précède ouvre le mode sudo pour 10 minutes (ADR 0034) : la ré-authentification sur 403
// est éprouvée par les tests du client et du serveur.
const RUN = Date.now().toString(36)
const EMAIL = `reinitialisation.${RUN}@exemple.ca`
const PASSWORD = 'fjord-tuque-raquette'
let lostKey = ''
let newKey = ''

test.describe.configure({ mode: 'serial' })

async function loginOnNewDevice(page: Page) {
  await page.goto('/connexion')
  await page.getByLabel('Identifiant ou courriel').fill(EMAIL)
  await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page.getByRole('heading', { name: 'Restaurer vos clés' })).toBeVisible()
}

async function restore(page: Page, key: string) {
  await page.getByLabel('Groupe 1 sur 8').fill(key)
  await page.getByRole('button', { name: 'Restaurer mes clés' }).click()
}

test('KEY-02 : un compte et sa clé de récupération, qui sera perdue', async ({ page, request }) => {
  lostKey = await registerAccount(page, request, {
    email: EMAIL,
    displayName: 'Réinitialisation Essai',
    password: PASSWORD,
  })
})

test('KEY-34 : sans sa clé, un nouvel appareil confirme, reçoit une nouvelle clé et entre dans SEAL', async ({
  page,
}) => {
  await loginOnNewDevice(page)
  await expect(page.getByRole('heading', { name: 'Clé de récupération perdue ?' })).toBeVisible()

  await page.getByRole('button', { name: 'Réinitialiser mon identité…' }).click()
  const confirm = page.getByRole('alertdialog', { name: 'Réinitialiser votre identité ?' })
  await expect(confirm.getByText('Cette action est irréversible.')).toBeVisible()
  await expect(
    confirm.getByText(
      'Vos anciens messages deviendront illisibles et vos contacts verront que votre clé a changé. Une nouvelle clé de récupération vous sera remise.',
    ),
  ).toBeVisible()
  await confirm.getByRole('button', { name: 'Réinitialiser', exact: true }).click()

  newKey = await confirmRecoveryKey(page)
  await expect(page.getByRole('dialog')).toBeHidden()
  await expect(page.getByRole('heading', { name: 'Restaurer vos clés' })).toBeHidden()
  await expect(page.getByRole('heading', { level: 1, name: 'seal' })).toBeVisible()
  expect(newKey).not.toBe(lostKey)
})

test('KEY-34 : sur un autre appareil, l’ancienne clé ne restaure plus rien, la nouvelle restaure l’identité', async ({
  page,
}) => {
  await loginOnNewDevice(page)

  await restore(page, lostKey)
  await expect(page.getByRole('alert')).toHaveText(
    'Cette clé de récupération ne correspond pas à votre coffre.',
  )
  await restore(page, newKey)

  await expect(page.getByRole('heading', { name: 'Restaurer vos clés' })).toBeHidden()
  await expect(page.getByRole('heading', { level: 1, name: 'seal' })).toBeVisible()
})
