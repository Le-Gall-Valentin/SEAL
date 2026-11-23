import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { type Account, SessionApiProvider, useCurrentAccount } from '@/entities/session'
import { ApiError } from '@/shared/lib'
import { EmailChangeApiProvider } from '../model/emailChangeApiPort'
import { EmailCard } from './EmailCard'

const ACCOUNT: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Valentin Dupont',
  email: 'valentin.dupont@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

interface FakeEmailChangeApi {
  readonly request: (email: string) => Promise<void>
  readonly confirm: (code: string) => Promise<Account>
}

function Screen({ onReauthRequired }: { readonly onReauthRequired: (retry: () => void) => void }) {
  const account = useCurrentAccount().data
  return account == null ? null : (
    <EmailCard account={account} onReauthRequired={onReauthRequired} />
  )
}

async function renderCard(
  api: FakeEmailChangeApi,
  onReauthRequired = vi.fn<(retry: () => void) => void>(),
  account: Account = ACCOUNT,
) {
  const screen = await render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider
        api={{ me: () => Promise.resolve(account), reloadCsrf: () => Promise.resolve() }}
      >
        <EmailChangeApiProvider api={api}>
          <Screen onReauthRequired={onReauthRequired} />
        </EmailChangeApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  await screen.getByRole('button', { name: 'Changer de courriel' }).click()
  return { screen, onReauthRequired }
}

async function typeCode(code: string) {
  await userEvent.keyboard(code)
}

describe('ACC-02 : « Changer de courriel »', () => {
  it('ACC-02 : adresse, code reçu, confirmation ; la carte montre la nouvelle adresse et l’annonce', async () => {
    const requested: string[] = []
    const confirmed: string[] = []
    const { screen } = await renderCard({
      request: (email) => {
        requested.push(email)
        return Promise.resolve()
      },
      confirm: (code) => {
        confirmed.push(code)
        return Promise.resolve({ ...ACCOUNT, email: 'neuve@exemple.ca' })
      },
    })

    const dialog = screen.getByRole('dialog', { name: 'Changer de courriel' })
    await expect.element(dialog.getByText('Étape 1 sur 2')).toBeVisible()
    await dialog.getByLabelText('Nouveau courriel').fill('  neuve@exemple.ca ')
    await dialog.getByRole('button', { name: 'Envoyer le code' }).click()

    const codeStep = screen.getByRole('dialog', { name: 'Saisissez le code' })
    await expect.element(codeStep.getByText('Étape 2 sur 2')).toBeVisible()
    await expect
      .element(
        codeStep.getByText(
          'Nous avons envoyé un code à 6 chiffres à neuve@exemple.ca. Il expire dans 10 minutes.',
        ),
      )
      .toBeVisible()
    await codeStep.getByLabelText('Chiffre 1 sur 6').click()
    await typeCode('503271')
    await codeStep.getByRole('button', { name: 'Confirmer' }).click()

    await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument()
    await expect.element(screen.getByText('neuve@exemple.ca')).toBeVisible()
    await expect.element(screen.getByRole('status')).toHaveTextContent('Courriel changé.')
    expect(requested).toEqual(['neuve@exemple.ca'])
    expect(confirmed).toEqual(['503271'])
  })

  it('ACC-02 : l’adresse actuelle, même en majuscules, ou une adresse mal formée ne part pas', async () => {
    const { screen } = await renderCard({
      request: () => Promise.reject(new Error('non attendu')),
      confirm: () => Promise.reject(new Error('non attendu')),
    })

    const send = screen.getByRole('button', { name: 'Envoyer le code' })
    await screen.getByLabelText('Nouveau courriel').fill('Valentin.Dupont@UQAC.ca')
    await expect.element(send).toBeDisabled()
    await screen.getByLabelText('Nouveau courriel').fill('pas-une-adresse')
    await userEvent.tab()
    await expect.element(screen.getByText('Adresse invalide.')).toBeVisible()
    await expect.element(send).toBeDisabled()
  })

  it('AUTH-55, ADR 0035 : sans mode sudo, l’écran est appelé ; sa relance renvoie la même adresse et passe au code', async () => {
    const requested: string[] = []
    let sudo = false
    const { screen, onReauthRequired } = await renderCard({
      request: (email) => {
        requested.push(email)
        return sudo ? Promise.resolve() : Promise.reject(new ApiError(403, 'REAUTH_REQUIRED'))
      },
      confirm: () => Promise.reject(new Error('non attendu')),
    })

    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    await vi.waitFor(() => {
      expect(onReauthRequired).toHaveBeenCalledTimes(1)
    })
    sudo = true
    const retry = onReauthRequired.mock.calls[0]?.[0]
    retry?.()

    await expect.element(screen.getByRole('dialog', { name: 'Saisissez le code' })).toBeVisible()
    expect(requested).toEqual(['neuve@exemple.ca', 'neuve@exemple.ca'])
  })

  it('ACC-02 : code refusé, « Code incorrect. » et cases vidées ; code expiré, son texte ; « Renvoyer le code » renvoie la même adresse', async () => {
    const requested: string[] = []
    const refusals = [new ApiError(422, 'INVALID_CODE'), new ApiError(422, 'CODE_EXPIRED')]
    const { screen } = await renderCard({
      request: (email) => {
        requested.push(email)
        return Promise.resolve()
      },
      confirm: () => Promise.reject(refusals.shift() ?? new Error('non attendu')),
    })

    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    await screen.getByLabelText('Chiffre 1 sur 6').click()
    await typeCode('111111')
    await screen.getByRole('button', { name: 'Confirmer' }).click()
    await expect.element(screen.getByText('Code incorrect.')).toBeVisible()
    await expect.element(screen.getByLabelText('Chiffre 1 sur 6')).toHaveValue('')

    await screen.getByLabelText('Chiffre 1 sur 6').click()
    await typeCode('222222')
    await screen.getByRole('button', { name: 'Confirmer' }).click()
    await expect
      .element(screen.getByText('Ce code a expiré. Renvoyez-en un nouveau.'))
      .toBeVisible()

    await screen.getByRole('button', { name: 'Renvoyer le code' }).click()
    await vi.waitFor(() => {
      expect(requested).toEqual(['neuve@exemple.ca', 'neuve@exemple.ca'])
    })
    expect(screen.getByText('Ce code a expiré. Renvoyez-en un nouveau.').elements()).toHaveLength(0)
  })

  it('SEC-10 : la 4e demande de l’heure (429) annonce le délai et bloque le champ', async () => {
    const { screen } = await renderCard({
      request: () => Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 1800 })),
      confirm: () => Promise.reject(new Error('non attendu')),
    })

    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()

    await expect.element(screen.getByText('Trop de tentatives')).toBeVisible()
    await expect.element(screen.getByLabelText('Nouveau courriel')).toBeDisabled()
  })

  it('ADR 0035 : fermer ramène à l’étape 1, sans rien de saisi', async () => {
    const { screen } = await renderCard({
      request: () => Promise.resolve(),
      confirm: () => Promise.reject(new Error('non attendu')),
    })

    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await screen.getByRole('button', { name: 'Changer de courriel' }).click()

    await expect.element(screen.getByText('Étape 1 sur 2')).toBeVisible()
    await expect.element(screen.getByLabelText('Nouveau courriel')).toHaveValue('')
  })

  it('ACC-02 : après une réussite, rouvrir repart à l’étape 1, vide', async () => {
    const { screen } = await renderCard({
      request: () => Promise.resolve(),
      confirm: () => Promise.resolve({ ...ACCOUNT, email: 'neuve@exemple.ca' }),
    })

    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    await typeCode('503271')
    await screen.getByRole('button', { name: 'Confirmer' }).click()
    await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument()
    await screen.getByRole('button', { name: 'Changer de courriel' }).click()

    await expect.element(screen.getByText('Étape 1 sur 2')).toBeVisible()
    await expect.element(screen.getByLabelText('Nouveau courriel')).toHaveValue('')
  })

  it('ACC-02, ADR 0035 : à l’étape 2 et après un code refusé, la saisie reprend à la case 1', async () => {
    const { screen } = await renderCard({
      request: () => Promise.resolve(),
      confirm: () => Promise.reject(new ApiError(422, 'INVALID_CODE')),
    })

    await expect.element(screen.getByLabelText('Nouveau courriel')).toHaveFocus()
    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    await expect.element(screen.getByLabelText('Chiffre 1 sur 6')).toHaveFocus()
    await typeCode('111111')
    await screen.getByRole('button', { name: 'Confirmer' }).click()

    await expect.element(screen.getByText('Code incorrect.')).toBeVisible()
    await expect.element(screen.getByLabelText('Chiffre 1 sur 6')).toHaveFocus()
  })

  it('ACC-02 : une adresse refusée (400) reste signalée et ne repart pas ; corrigée, elle peut partir', async () => {
    const requested: string[] = []
    const { screen } = await renderCard({
      request: (email) => {
        requested.push(email)
        return Promise.reject(new ApiError(400, 'VALIDATION_FAILED'))
      },
      confirm: () => Promise.reject(new Error('non attendu')),
    })

    await screen.getByLabelText('Nouveau courriel').fill('refusee@exemple.ca')
    const send = screen.getByRole('button', { name: 'Envoyer le code' })
    await send.click()
    await expect.element(screen.getByText('Adresse invalide.')).toBeVisible()
    await expect.element(send).toBeDisabled()
    await screen.getByLabelText('Nouveau courriel').fill('acceptee@exemple.ca')

    expect(screen.getByText('Adresse invalide.').elements()).toHaveLength(0)
    await expect.element(send).toBeEnabled()
    expect(requested).toEqual(['refusee@exemple.ca'])
  })

  it('ACC-02 : l’adresse actuelle est signalée « Adresse invalide. » une fois le champ quitté', async () => {
    const { screen } = await renderCard({
      request: () => Promise.reject(new Error('non attendu')),
      confirm: () => Promise.reject(new Error('non attendu')),
    })

    await screen.getByLabelText('Nouveau courriel').fill('valentin.dupont@uqac.ca')
    await userEvent.tab()

    await expect.element(screen.getByText('Adresse invalide.')).toBeVisible()
  })

  it('ACC-02, ADR 0024 (point 9) : un renvoi réussi l’annonce, « Code envoyé à … »', async () => {
    const { screen } = await renderCard({
      request: () => Promise.resolve(),
      confirm: () => Promise.reject(new Error('non attendu')),
    })

    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    expect(screen.getByText('Code envoyé à neuve@exemple.ca.').elements()).toHaveLength(0)
    await screen.getByRole('button', { name: 'Renvoyer le code' }).click()

    await expect.element(screen.getByText('Code envoyé à neuve@exemple.ca.')).toBeVisible()
  })

  it('SEC-02 : un courriel balisé s’affiche en texte dans la carte', async () => {
    const hostile = '"<img src=x onerror=alert(1)>"@uqac.ca'
    const { screen } = await renderCard(
      {
        request: () => Promise.reject(new Error('non attendu')),
        confirm: () => Promise.reject(new Error('non attendu')),
      },
      vi.fn<(retry: () => void) => void>(),
      { ...ACCOUNT, email: hostile },
    )

    await expect.element(screen.getByText(hostile, { exact: true })).toBeVisible()
    expect(document.querySelectorAll('img')).toHaveLength(0)
  })
})
