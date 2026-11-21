import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { type IOAuthProvidersApi, OAuthProvidersApiProvider } from '@/entities/oauth-provider'
import { OAuthButtons } from './OAuthButtons'
import { OAuthLoginAlert } from './OAuthLoginAlert'

function withProviders(api: IOAuthProvidersApi, children: ReactNode) {
  return (
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <OAuthProvidersApiProvider api={api}>{children}</OAuthProvidersApiProvider>
    </QueryClientProvider>
  )
}

describe('ADR 0033 : boutons « Continuer avec… »', () => {
  it('AUTH-30, AUTH-35 : un lien de même origine par fournisseur configuré, dans l’ordre, après « ou »', async () => {
    const screen = await render(
      withProviders({ providers: () => Promise.resolve(['google', 'github']) }, <OAuthButtons />),
    )

    const google = screen.getByRole('link', { name: 'Continuer avec Google' })
    const github = screen.getByRole('link', { name: 'Continuer avec GitHub' })
    await expect.element(google).toHaveAttribute('href', '/oauth2/authorization/google')
    await expect.element(github).toHaveAttribute('href', '/oauth2/authorization/github')
    await expect.element(screen.getByText('ou', { exact: true })).toBeVisible()
    expect(
      screen
        .getByRole('link')
        .elements()
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/oauth2/authorization/google', '/oauth2/authorization/github'])
    // Les logos sont décoratifs : le nom accessible n'est que le texte.
    expect(google.element().querySelector('svg')?.getAttribute('aria-hidden')).toBe('true')
  })

  it('OPS-03, ADR 0024 (point 3) : sans fournisseur, ni boutons ni « ou »', async () => {
    const screen = await render(withProviders({ providers: () => Promise.resolve([]) }, <p>fin</p>))
    const empty = await render(
      withProviders({ providers: () => Promise.resolve([]) }, <OAuthButtons />),
    )

    await expect.element(screen.getByText('fin')).toBeVisible()
    await expect.element(empty.getByText('ou', { exact: true })).not.toBeInTheDocument()
    expect(empty.container.querySelectorAll('a')).toHaveLength(0)
  })

  it('ADR 0007 (point 9) : fournisseurs illisibles, aucun bouton annoncé', async () => {
    const screen = await render(
      withProviders({ providers: () => Promise.reject(new Error('réseau')) }, <OAuthButtons />),
    )

    await expect.element(screen.getByText('ou', { exact: true })).not.toBeInTheDocument()
  })
})

describe('ADR 0033 (point 1) : alerte du retour OAuth', () => {
  it.each([
    [
      'OAUTH_EMAIL_IN_USE',
      'Ce courriel a déjà un compte',
      'Connectez-vous avec votre méthode habituelle.',
    ],
    ['RATE_LIMITED', 'Trop de tentatives', 'Réessayez plus tard.'],
    ['REQUEST_REJECTED', 'La connexion n’a pas abouti', 'Réessayez.'],
  ] as const)('AUTH-33, SEC-10 : %s, titre et phrase', async (code, title, body) => {
    const screen = await render(<OAuthLoginAlert error={code} />)

    const alert = screen.getByRole('alert')
    await expect.element(alert).toHaveTextContent(`${title}${body}`)
  })
})
