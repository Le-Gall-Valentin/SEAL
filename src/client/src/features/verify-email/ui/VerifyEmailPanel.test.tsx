import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { describe, expect, it } from 'vitest'
import { userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import {
  type Account,
  type ISessionApi,
  SessionApiProvider,
  useCurrentAccount,
} from '@/entities/session'
import { ApiError } from '@/shared/lib'
import { VerifyEmailApiProvider } from '../model/VerifyEmailApiProvider'
import { VerifyEmailPanel } from './VerifyEmailPanel'

type IVerifyEmailApi = ComponentProps<typeof VerifyEmailApiProvider>['api']

const PENDING: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@uqac.ca',
  status: 'PENDING_EMAIL',
  verification: { codeExpiresAt: new Date(Date.now() + 90_000), resendsRemaining: 2 },
}

// Le panneau reçoit le compte relu par /me, comme dans la page.
function Panel() {
  const account = useCurrentAccount()
  return account.data ? (
    <>
      <p>{account.data.status}</p>
      <VerifyEmailPanel email={account.data.email} verification={account.data.verification} />
    </>
  ) : null
}

async function renderPanel(api: Partial<IVerifyEmailApi>, initial: Account = PENDING) {
  let account = initial
  const calls: string[] = []
  const verifyApi: IVerifyEmailApi = {
    verify: async (code) => {
      calls.push(`verify ${code}`)
      await (api.verify ?? (() => Promise.resolve()))(code)
      account = { ...account, status: 'ACTIVE', verification: null }
    },
    resend: async () => {
      calls.push('resend')
      await (api.resend ?? (() => Promise.resolve()))()
      account = {
        ...account,
        verification: {
          codeExpiresAt: new Date(Date.now() + 600_000),
          resendsRemaining: (account.verification?.resendsRemaining ?? 1) - 1,
        },
      }
    },
    changeEmail: async (email) => {
      calls.push(`change ${email}`)
      await (api.changeEmail ?? (() => Promise.resolve()))(email)
      account = { ...account, email }
    },
  }
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(account),
    reloadCsrf: () => Promise.resolve(),
  }
  const screen = await render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <SessionApiProvider api={sessionApi}>
        <VerifyEmailApiProvider api={verifyApi}>
          <Panel />
        </VerifyEmailApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
  return { screen, calls }
}

type Screen = Awaited<ReturnType<typeof renderPanel>>['screen']
const box = (screen: Screen, index: number) =>
  screen.getByLabelText(`Code de vérification — ${String(index)}/6`)

describe('AUTH-14, AUTH-17 : vérification du courriel', () => {
  it('AUTH-14 : six cases numériques, la première proposée au remplissage du code par le système', async () => {
    const { screen } = await renderPanel({})

    await expect.element(screen.getByRole('group', { name: 'Code de vérification' })).toBeVisible()
    await expect.element(box(screen, 1)).toHaveAttribute('autocomplete', 'one-time-code')
    for (const index of [1, 2, 3, 4, 5, 6]) {
      await expect.element(box(screen, index)).toHaveAttribute('inputmode', 'numeric')
    }
    await expect.element(screen.getByText('5 essais par code')).toBeVisible()
  })

  it('AUTH-14 : chaque chiffre fait avancer le focus ; « Vérifier » attend les six', async () => {
    const { screen } = await renderPanel({})
    const verify = screen.getByRole('button', { name: 'Vérifier' })

    await box(screen, 1).click()
    await userEvent.keyboard('50327')
    await expect.element(box(screen, 6)).toHaveFocus()
    await expect.element(verify).toBeDisabled()
    await userEvent.keyboard('1')
    await expect.element(verify).toBeEnabled()
  })

  it('AUTH-14, AUTH-16 : un code collé remplit les cases ; la vérification rend le compte actif', async () => {
    const { screen, calls } = await renderPanel({})

    await box(screen, 1).fill('503271')
    await expect.element(box(screen, 6)).toHaveValue('1')
    await screen.getByRole('button', { name: 'Vérifier' }).click()

    await expect.element(screen.getByText('ACTIVE')).toBeVisible()
    expect(calls).toEqual(['verify 503271'])
  })

  it('AUTH-14 : compte à rebours tiré de codeExpiresAt', async () => {
    const { screen } = await renderPanel({})

    await expect
      .element(screen.getByText(/^Expire dans/))
      .toMatchTextContent(/Expire dans 1:(30|29)/)
  })

  it('ADR 0024 : sans code vivant, « Ce code a expiré. Renvoyez-en un nouveau. »', async () => {
    const { screen } = await renderPanel(
      {},
      {
        ...PENDING,
        verification: { codeExpiresAt: null, resendsRemaining: 2 },
      },
    )

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Ce code a expiré. Renvoyez-en un nouveau.')
  })

  it('ADR 0024 : INVALID_CODE affiche « Code incorrect. » et vide les cases', async () => {
    const { screen } = await renderPanel({
      verify: () => Promise.reject(new ApiError(422, 'INVALID_CODE')),
    })
    await box(screen, 1).fill('111111')
    await screen.getByRole('button', { name: 'Vérifier' }).click()

    await expect.element(screen.getByRole('alert')).toHaveTextContent('Code incorrect.')
    await expect.element(box(screen, 1)).toHaveValue('')
    await expect.element(box(screen, 1)).toHaveFocus()
  })

  it('ADR 0024 : CODE_EXPIRED au retour du serveur', async () => {
    const { screen } = await renderPanel({
      verify: () => Promise.reject(new ApiError(422, 'CODE_EXPIRED')),
    })
    await box(screen, 1).fill('111111')
    await screen.getByRole('button', { name: 'Vérifier' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Ce code a expiré. Renvoyez-en un nouveau.')
  })

  it.each([
    [2, '2 renvois restants cette heure'],
    [1, '1 renvoi restant cette heure'],
    [0, 'Aucun renvoi restant cette heure'],
  ])('ADR 0024 : %i renvoi(s) : « %s »', async (remaining, text) => {
    const { screen } = await renderPanel(
      {},
      {
        ...PENDING,
        verification: { codeExpiresAt: new Date(Date.now() + 60_000), resendsRemaining: remaining },
      },
    )

    await expect.element(screen.getByText(text)).toBeVisible()
    const resend = screen.getByRole('button', { name: 'Renvoyer le code' })
    await (remaining === 0
      ? expect.element(resend).toBeDisabled()
      : expect.element(resend).toBeEnabled())
  })

  it('AUTH-14 : renvoi, « Code envoyé à … », renvois restants relus', async () => {
    const { screen, calls } = await renderPanel({})
    await screen.getByRole('button', { name: 'Renvoyer le code' }).click()

    await expect
      .element(screen.getByRole('status'))
      .toHaveTextContent('Code envoyé à valentin@uqac.ca.')
    await expect.element(screen.getByText('1 renvoi restant cette heure')).toBeVisible()
    expect(calls).toEqual(['resend'])
  })

  it('SEC-10 : renvoi refusé en 429, « Trop de tentatives »', async () => {
    const { screen } = await renderPanel({
      resend: () => Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 3600 })),
    })
    await screen.getByRole('button', { name: 'Renvoyer le code' }).click()

    await expect.element(screen.getByRole('alert')).toMatchTextContent('Réessayez dans 60 minutes.')
  })

  it('AUTH-17 : « Modifier le courriel » ouvre le champ sur place, puis envoie le code à la nouvelle adresse', async () => {
    const { screen, calls } = await renderPanel({})
    await expect.element(screen.getByText(/Votre compte reste inactif/)).toBeVisible()
    await screen.getByRole('button', { name: 'Modifier le courriel' }).click()

    const email = screen.getByLabelText('Courriel', { exact: true })
    await email.fill('noah@')
    await screen.getByRole('button', { name: 'Annuler' }).hover()
    await email.fill('noah@uqac.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()

    await expect
      .element(screen.getByRole('status'))
      .toHaveTextContent('Code envoyé à noah@uqac.ca.')
    await expect.element(screen.getByLabelText('Courriel', { exact: true })).not.toBeInTheDocument()
    expect(calls).toEqual(['change noah@uqac.ca'])
  })

  it('AUTH-17 : adresse mal formée, « Adresse invalide. » ; « Annuler » referme le champ', async () => {
    const { screen } = await renderPanel({})
    await screen.getByRole('button', { name: 'Modifier le courriel' }).click()
    await screen.getByLabelText('Courriel', { exact: true }).fill('noah@')
    await screen.getByLabelText('Code de vérification — 1/6').click()

    await expect.element(screen.getByText('Adresse invalide.')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Envoyer le code' })).toBeDisabled()
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await expect.element(screen.getByLabelText('Courriel', { exact: true })).not.toBeInTheDocument()
  })
})
