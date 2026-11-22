import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { page } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { type LoginMethods, LoginMethodsApiProvider } from '@/entities/account'
import { OAuthProvidersApiProvider } from '@/entities/oauth-provider'
import { PasswordBlocklistProvider } from '@/entities/password'
import { type Account, SessionApiProvider } from '@/entities/session'
import { ProfileApiProvider } from '@/features/change-display-name'
import { EmailChangeApiProvider } from '@/features/change-email'
import { PasswordChangeApiProvider } from '@/features/change-password'
import { LogoutApiProvider } from '@/features/logout'
import { ReauthApiProvider } from '@/features/reauthenticate'
import { ApiError } from '@/shared/lib'
import { SettingsPage } from './SettingsPage'

const ACCOUNT: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Valentin Dupont',
  email: 'valentin.dupont@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}
const never = () => Promise.reject(new Error('non attendu'))

interface Fakes {
  readonly methods?: LoginMethods
  readonly requestEmail?: (email: string) => Promise<void>
  readonly reauthenticate?: (password: string) => Promise<void>
}

function Providers({ fakes, children }: { readonly fakes: Fakes; readonly children: ReactNode }) {
  return (
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <SessionApiProvider
        api={{ me: () => Promise.resolve(ACCOUNT), reloadCsrf: () => Promise.resolve() }}
      >
        <PasswordBlocklistProvider api={{ load: () => Promise.resolve([]) }}>
          <LoginMethodsApiProvider
            api={{
              loginMethods: () =>
                Promise.resolve(fakes.methods ?? { passwordSet: true, providers: [] }),
            }}
          >
            <OAuthProvidersApiProvider
              api={{ providers: () => Promise.resolve(['google', 'github']) }}
            >
              <ReauthApiProvider api={{ reauthenticate: fakes.reauthenticate ?? never }}>
                <ProfileApiProvider api={{ changeDisplayName: never }}>
                  <PasswordChangeApiProvider api={{ changePassword: never }}>
                    <EmailChangeApiProvider
                      api={{ request: fakes.requestEmail ?? never, confirm: never }}
                    >
                      <LogoutApiProvider api={{ logout: () => Promise.resolve() }}>
                        {children}
                      </LogoutApiProvider>
                    </EmailChangeApiProvider>
                  </PasswordChangeApiProvider>
                </ProfileApiProvider>
              </ReauthApiProvider>
            </OAuthProvidersApiProvider>
          </LoginMethodsApiProvider>
        </PasswordBlocklistProvider>
      </SessionApiProvider>
    </QueryClientProvider>
  )
}

async function renderAt(entry: string, width: number, height: number, fakes: Fakes = {}) {
  await page.viewport(width, height)
  const router = createMemoryRouter([{ path: '/parametres', element: <SettingsPage /> }], {
    initialEntries: [entry],
  })
  const screen = await render(
    <Providers fakes={fakes}>
      <RouterProvider router={router} />
    </Providers>,
  )
  return { router, screen }
}

describe('ACC-01 à ACC-03, ADR 0035 : écran Paramètres', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('Parametres-Ordinateur : navigation des sections (Compte seule), puis Profil, Courriel et Mot de passe', async () => {
    const { screen } = await renderAt('/parametres', 1440, 1100)

    const sections = screen.getByRole('navigation', { name: 'Sections des paramètres' })
    await expect
      .element(sections.getByRole('heading', { level: 1, name: 'Paramètres' }))
      .toBeVisible()
    await expect
      .element(sections.getByRole('link', { name: 'Compte' }))
      .toHaveAttribute('aria-current', 'page')
    expect(sections.getByRole('link').elements()).toHaveLength(1)
    await expect.element(sections.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    await expect.element(screen.getByRole('heading', { level: 2, name: 'Compte' })).toBeVisible()
    await expect.element(screen.getByRole('heading', { level: 3, name: 'Profil' })).toBeVisible()
    await expect.element(screen.getByRole('heading', { level: 3, name: 'Courriel' })).toBeVisible()
    await expect
      .element(screen.getByRole('heading', { level: 3, name: 'Mot de passe' }))
      .toBeVisible()
    expect(screen.getByRole('heading', { name: 'Comptes liés' }).elements()).toHaveLength(0)
  })

  it('ADR 0035, point 4 : un compte sans mot de passe ne voit pas la carte Mot de passe', async () => {
    const { screen } = await renderAt('/parametres', 1440, 1100, {
      methods: { passwordSet: false, providers: ['google'] },
    })

    await expect.element(screen.getByRole('heading', { level: 3, name: 'Courriel' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Mot de passe' }).elements()).toHaveLength(0)
  })

  it('AUTH-55, ADR 0035 : sans mode sudo, « Confirmez votre identité » s’ouvre ; le mot de passe accepté relance la demande', async () => {
    let sudo = false
    const requested: string[] = []
    const passwords: string[] = []
    const { screen } = await renderAt('/parametres', 1440, 1100, {
      requestEmail: (email) => {
        requested.push(email)
        return sudo ? Promise.resolve() : Promise.reject(new ApiError(403, 'REAUTH_REQUIRED'))
      },
      reauthenticate: (password) => {
        passwords.push(password)
        sudo = true
        return Promise.resolve()
      },
    })

    await screen.getByRole('button', { name: 'Changer de courriel' }).click()
    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    const reauth = screen.getByRole('dialog', { name: 'Confirmez votre identité' })
    await reauth.getByLabelText('Mot de passe', { exact: true }).fill('phrase secrète')
    await reauth.getByRole('button', { name: 'Confirmer' }).click()

    await expect.element(screen.getByRole('dialog', { name: 'Saisissez le code' })).toBeVisible()
    expect(
      screen.getByRole('dialog', { name: 'Confirmez votre identité' }).elements(),
    ).toHaveLength(0)
    expect(passwords).toEqual(['phrase secrète'])
    expect(requested).toEqual(['neuve@exemple.ca', 'neuve@exemple.ca'])
  })

  it('AUTH-55 : annuler la ré-authentification laisse le dialogue du courriel ouvert, adresse gardée, sans relance', async () => {
    const requested: string[] = []
    const { screen } = await renderAt('/parametres', 1440, 1100, {
      requestEmail: (email) => {
        requested.push(email)
        return Promise.reject(new ApiError(403, 'REAUTH_REQUIRED'))
      },
    })

    await screen.getByRole('button', { name: 'Changer de courriel' }).click()
    await screen.getByLabelText('Nouveau courriel').fill('neuve@exemple.ca')
    await screen.getByRole('button', { name: 'Envoyer le code' }).click()
    await screen
      .getByRole('dialog', { name: 'Confirmez votre identité' })
      .getByRole('button', { name: 'Annuler' })
      .click()

    await expect.element(screen.getByRole('dialog', { name: 'Changer de courriel' })).toBeVisible()
    await expect.element(screen.getByLabelText('Nouveau courriel')).toHaveValue('neuve@exemple.ca')
    expect(requested).toEqual(['neuve@exemple.ca'])
  })

  it('AUTH-55, ADR 0034 : un retour OAuth refusé s’annonce une fois, puis quitte l’adresse', async () => {
    const { router, screen } = await renderAt('/parametres?erreur=REQUEST_REJECTED', 1440, 1100)

    await expect
      .element(screen.getByText('La confirmation par le fournisseur a échoué. Réessayez.'))
      .toBeVisible()
    await expect.poll(() => router.state.location.search).toBe('')
    await expect
      .element(screen.getByText('La confirmation par le fournisseur a échoué. Réessayez.'))
      .toBeVisible()
  })

  it('SEC-01 : un code de retour inconnu n’affiche rien', async () => {
    const { screen } = await renderAt('/parametres?erreur=<b>piège</b>', 1440, 1100)

    await expect.element(screen.getByRole('heading', { level: 3, name: 'Profil' })).toBeVisible()
    expect(screen.getByRole('alert').elements()).toHaveLength(0)
  })

  it('Parametres-Telephone : carte du profil et liste des sections ; Compte s’ouvre en sous-page, puis « Paramètres » revient', async () => {
    const { router, screen } = await renderAt('/parametres', 390, 844)

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Paramètres' }))
      .toBeVisible()
    await expect.element(screen.getByText('@valentin.dupont')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Profil' }).elements()).toHaveLength(0)
    await screen
      .getByRole('navigation', { name: 'Sections des paramètres' })
      .getByRole('link', { name: /Compte/ })
      .click()

    expect(router.state.location.search).toBe('?section=compte')
    await expect.element(screen.getByRole('heading', { level: 1, name: 'Compte' })).toBeVisible()
    await expect.element(screen.getByRole('heading', { level: 3, name: 'Profil' })).toBeVisible()
    expect(screen.getByLabelText('Nom affiché').elements()).toHaveLength(1)
    await screen.getByRole('link', { name: 'Paramètres' }).click()
    await expect.element(screen.getByText('@valentin.dupont')).toBeVisible()
  })

  it('ADR 0035 : passer du téléphone à la tablette garde une seule section montée', async () => {
    const { screen } = await renderAt('/parametres?section=compte', 390, 844)
    await expect.element(screen.getByRole('heading', { level: 3, name: 'Profil' })).toBeVisible()

    await page.viewport(820, 1180)
    await expect
      .element(screen.getByRole('navigation', { name: 'Sections des paramètres' }))
      .toBeVisible()
    expect(screen.getByLabelText('Nom affiché').elements()).toHaveLength(1)
  })
})
