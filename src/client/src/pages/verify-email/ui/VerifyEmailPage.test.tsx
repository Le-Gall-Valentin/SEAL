import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import { type IVerifyEmailApi, VerifyEmailApiProvider } from '@/features/verify-email'
import { VerifyEmailPage } from './VerifyEmailPage'

describe('Écran Verification-Courriel', () => {
  it('AUTH-14 : titre et adresse du compte en attente, relus par /me', async () => {
    const sessionApi: ISessionApi = {
      me: () =>
        Promise.resolve({
          id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
          username: 'valentin',
          displayName: 'Valentin',
          email: 'valentin.dupont@uqac.ca',
          status: 'PENDING_EMAIL',
          verification: { codeExpiresAt: new Date(Date.now() + 60_000), resendsRemaining: 3 },
        }),
      reloadCsrf: () => Promise.resolve(),
    }
    const verifyApi: IVerifyEmailApi = {
      verify: () => Promise.resolve(),
      resend: () => Promise.resolve(),
      changeEmail: () => Promise.resolve(),
    }
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <SessionApiProvider api={sessionApi}>
          <LogoutApiProvider api={{ logout: () => Promise.resolve() }}>
            <VerifyEmailApiProvider api={verifyApi}>
              <VerifyEmailPage />
            </VerifyEmailApiProvider>
          </LogoutApiProvider>
        </SessionApiProvider>
      </QueryClientProvider>,
    )

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Vérifiez votre courriel' }))
      .toBeVisible()
    const address = screen.getByText('valentin.dupont@uqac.ca', { exact: true })
    expect(address.element().parentElement?.textContent).toBe(
      'Saisissez le code à 6 chiffres envoyé à valentin.dupont@uqac.ca.',
    )
    // ADR 0024, point 10 : la session restreinte a une issue.
    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  })
})
