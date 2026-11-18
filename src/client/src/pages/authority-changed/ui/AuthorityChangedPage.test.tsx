import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { type ILogoutApi, LogoutApiProvider } from '@/features/logout'
import { AuthorityChangedPage } from './AuthorityChangedPage'

async function renderPage(logoutApi: ILogoutApi) {
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider api={sessionApi}>
        <LogoutApiProvider api={logoutApi}>
          <AuthorityChangedPage />
        </LogoutApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
}

describe('KEY-11 : page « Autorité SEAL modifiée » (ADR 0027, point 1)', () => {
  it('KEY-11 : annonce le titre et le texte validés, avec « Se déconnecter » pour seule action', async () => {
    const screen = await renderPage({ logout: () => Promise.resolve() })

    await expect.element(screen.getByRole('alert')).toBeVisible()
    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Autorité SEAL modifiée' }))
      .toBeVisible()
    await expect
      .element(
        screen.getByText(
          'La clé de l’Autorité SEAL a changé depuis votre première connexion sur cet appareil. Par sécurité, SEAL est bloqué : prévenez l’administrateur du serveur.',
        ),
      )
      .toBeVisible()
    expect(screen.getByRole('button').elements()).toHaveLength(1)
    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  })

  it('AUTH-53 : « Se déconnecter » ferme la session derrière le blocage', async () => {
    const logout = vi.fn(() => Promise.resolve())
    const screen = await renderPage({ logout })

    await screen.getByRole('button', { name: 'Se déconnecter' }).click()

    await vi.waitFor(() => {
      expect(logout).toHaveBeenCalledOnce()
    })
  })

  it('ADR 0016 : la carte est centrée dans la page, avec des marges de 16 px', async () => {
    const screen = await renderPage({ logout: () => Promise.resolve() })

    expect(getComputedStyle(screen.getByRole('main').element())).toMatchObject({
      display: 'grid',
      alignItems: 'center',
      justifyItems: 'center',
      padding: '16px',
    })
  })
})
