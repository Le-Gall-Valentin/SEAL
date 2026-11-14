import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { render, renderHook } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '../model/passwordBlocklistPort'
import { usePasswordBlocklist } from '../model/usePasswordBlocklist'
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
    const { result } = await renderHook(() => usePasswordBlocklist(), {
      wrapper: wrapper(() => Promise.reject(new Error('chunk'))),
    })

    await expect.poll(() => result.current).toBeNull()
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
})
