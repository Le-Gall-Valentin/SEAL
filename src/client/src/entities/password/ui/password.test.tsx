import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { render, renderHook } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '../model/passwordBlocklistPort'
import { usePasswordBlocklist } from '../model/usePasswordBlocklist'
import { NewPasswordFields } from './NewPasswordFields'
import { CompositionNote, PasswordRulesList } from './PasswordRulesList'
import { StrengthMeter } from './StrengthMeter'

function wrapper(load: () => Promise<readonly string[]>) {
  return function Wrapper({ children }: { readonly children: ReactNode }) {
    return (
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <PasswordBlocklistProvider api={{ load }}>{children}</PasswordBlocklistProvider>
      </QueryClientProvider>
    )
  }
}

describe('AUTH-11 : règles et jauge du mot de passe (Inscription-Telephone)', () => {
  it('AUTH-11 : la liste de blocage chargée est repliée comme le serveur', async () => {
    const { result } = await renderHook(() => usePasswordBlocklist(), {
      wrapper: wrapper(() => Promise.resolve(['Mailcreated5240'])),
    })

    await expect.poll(() => result.current?.has('mailcreated5240')).toBe(true)
  })

  it('ADR 0024 : un échec du chargement laisse la liste absente (règle non vérifiée)', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = await renderHook(() => usePasswordBlocklist(), {
      wrapper: ({ children }: { readonly children: ReactNode }) => (
        <QueryClientProvider client={client}>
          <PasswordBlocklistProvider api={{ load: () => Promise.reject(new Error('chunk')) }}>
            {children}
          </PasswordBlocklistProvider>
        </QueryClientProvider>
      ),
    })

    // Le chargement a bien échoué avant qu'on lise la liste : absente, jamais vide.
    await expect.poll(() => client.getQueryState(['password-blocklist'])?.status).toBe('error')
    expect(result.current).toBeNull()
  })

  it('AUTH-11 : textes de la maquette ; respectée en coche, sinon cercle vide gris (ADR 0024)', async () => {
    const screen = await render(
      <PasswordRulesList rules={{ length: 'met', context: 'unmet', blocklist: 'unknown' }}>
        <CompositionNote />
      </PasswordRulesList>,
    )

    await expect
      .element(screen.getByRole('listitem').filter({ hasText: '15 caractères ou plus' }))
      .toHaveAttribute('data-state', 'met')
    await expect
      .element(
        screen
          .getByRole('listitem')
          .filter({ hasText: 'Ni votre identifiant, ni votre courriel, ni « seal »' }),
      )
      .toHaveAttribute('data-state', 'unmet')
    await expect
      .element(
        screen
          .getByRole('listitem')
          .filter({ hasText: 'Absent des mots de passe les plus courants' }),
      )
      .toHaveAttribute('data-state', 'unmet')
    await expect
      .element(screen.getByText('Aucune règle de composition : une phrase de passe suffit.'))
      .toBeVisible()
    // ADR 0024, point 10 : l'état de chaque règle est dit aux lecteurs d'écran.
    const rule = (text: string) => screen.getByRole('listitem').filter({ hasText: text })
    await expect
      .element(rule('15 caractères ou plus').getByText('respectée', { exact: true }))
      .toBeInTheDocument()
    await expect
      .element(rule('Absent des mots de passe').getByText('non respectée', { exact: true }))
      .toBeInTheDocument()
    // ADR 0007, point 9 : AUTH-12 n'existe pas encore.
    await expect.element(screen.getByText(/divulgués/)).not.toBeInTheDocument()
  })

  it.each([
    [0, 'Insuffisant'],
    [1, 'Passable'],
    [2, 'Correct'],
    [3, 'Bon'],
    [4, 'Excellent'],
  ] as const)('ADR 0007, point 6 : jauge au niveau %i, « %s »', async (strength, label) => {
    const screen = await render(<StrengthMeter strength={strength} />)

    const meter = screen.getByRole('meter', { name: 'Robustesse' })
    await expect.element(meter).toHaveAttribute('aria-valuenow', String(strength))
    await expect.element(meter).toHaveAttribute('aria-valuetext', label)
    await expect.element(screen.getByText(label, { exact: true })).toBeVisible()
    expect(meter.element().querySelectorAll('[data-filled="true"]')).toHaveLength(strength)
  })

  it('AUTH-11 : bloc mot de passe et confirmation, commun à l’inscription et au nouveau mot de passe', async () => {
    const screen = await render(
      <NewPasswordFields
        passwordLabel="Nouveau mot de passe"
        password="fjord-tuque-raquette"
        confirm="fjord-tuque-raquett"
        onPasswordChange={() => undefined}
        onConfirmChange={() => undefined}
        onConfirmBlur={() => undefined}
        confirmMismatch
        rules={{ length: 'met', context: 'met', blocklist: 'met' }}
        strength={3}
      />,
    )

    const password = screen.getByLabelText('Nouveau mot de passe', { exact: true })
    await expect.element(password).toHaveAttribute('autocomplete', 'new-password')
    await expect.element(password).toHaveAttribute('maxlength', '512')
    await expect
      .element(password)
      .toHaveAccessibleDescription(/^Robustesse Bon 15 caractères ou plus respectée/)
    await expect
      .element(screen.getByLabelText('Confirmer le mot de passe', { exact: true }))
      .toHaveAttribute('aria-invalid', 'true')
    await expect.element(screen.getByText('Les mots de passe ne correspondent pas.')).toBeVisible()
  })
})
