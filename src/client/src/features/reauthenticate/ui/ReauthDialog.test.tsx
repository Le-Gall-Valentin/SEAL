import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { type LoginMethods, LoginMethodsApiProvider } from '@/entities/account'
import { type OAuthProviderId, OAuthProvidersApiProvider } from '@/entities/oauth-provider'
import { PasswordBlocklistProvider } from '@/entities/password'
import { ApiError } from '@/shared/lib'
import { ReauthApiProvider } from '../model/reauthApiPort'
import { ReauthDialog } from './ReauthDialog'

const PASSWORD_ONLY: LoginMethods = { passwordSet: true, providers: [] }

interface FakeReauthApi {
  readonly reauthenticate: (password: string) => Promise<void>
}

function Harness({ onReauthenticated }: { readonly onReauthenticated: () => void }) {
  const [open, setOpen] = useState(true)
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setOpen(true)
        }}
      >
        Rouvrir
      </button>
      <ReauthDialog
        open={open}
        onReauthenticated={() => {
          setOpen(false)
          onReauthenticated()
        }}
        onCancel={() => {
          setOpen(false)
        }}
      />
    </>
  )
}

async function renderDialog(
  methods: LoginMethods | (() => Promise<LoginMethods>),
  api: FakeReauthApi,
  configured: readonly OAuthProviderId[] = ['google', 'github'],
  // Comme le client de l'application (app/providers/queryClient) : une mutation sans écran qui la suit est oubliée.
  client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { gcTime: 0 } },
  }),
) {
  const onReauthenticated = vi.fn()
  const screen = await render(
    <QueryClientProvider client={client}>
      <PasswordBlocklistProvider api={{ load: () => Promise.resolve([]) }}>
        <LoginMethodsApiProvider
          api={{
            loginMethods: typeof methods === 'function' ? methods : () => Promise.resolve(methods),
          }}
        >
          <OAuthProvidersApiProvider api={{ providers: () => Promise.resolve(configured) }}>
            <ReauthApiProvider api={api}>
              <Harness onReauthenticated={onReauthenticated} />
            </ReauthApiProvider>
          </OAuthProvidersApiProvider>
        </LoginMethodsApiProvider>
      </PasswordBlocklistProvider>
    </QueryClientProvider>,
  )
  return { screen, onReauthenticated }
}

describe('AUTH-55, ADR 0035 : « Confirmez votre identité »', () => {
  it('AUTH-55 : mot de passe accepté, l’écran est prévenu une fois et le dialogue se ferme', async () => {
    const sent: string[] = []
    const { screen, onReauthenticated } = await renderDialog(PASSWORD_ONLY, {
      reauthenticate: (password) => {
        sent.push(password)
        return Promise.resolve()
      },
    })

    const dialog = screen.getByRole('dialog', { name: 'Confirmez votre identité' })
    await expect.element(dialog.getByText('Cette action est protégée.')).toBeVisible()
    await expect
      .element(
        dialog.getByText(
          'Saisissez votre mot de passe pour continuer. La confirmation vaut 10 minutes.',
        ),
      )
      .toBeVisible()
    const submit = dialog.getByRole('button', { name: 'Confirmer' })
    await expect.element(submit).toBeDisabled()
    await dialog.getByLabelText('Mot de passe', { exact: true }).fill('phrase secrète')
    await submit.click()

    await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument()
    expect(sent).toEqual(['phrase secrète'])
    expect(onReauthenticated).toHaveBeenCalledTimes(1)
  })

  it('AUTH-55 : mot de passe refusé, « Mot de passe incorrect. » sous le champ vidé ; rien n’est relancé', async () => {
    const { screen, onReauthenticated } = await renderDialog(PASSWORD_ONLY, {
      reauthenticate: () => Promise.reject(new ApiError(401, 'INVALID_CREDENTIALS')),
    })

    const field = screen.getByLabelText('Mot de passe', { exact: true })
    await field.fill('mauvais')
    await screen.getByRole('button', { name: 'Confirmer' }).click()

    await expect.element(screen.getByText('Mot de passe incorrect.')).toBeVisible()
    await expect.element(field).toHaveValue('')
    expect(onReauthenticated).not.toHaveBeenCalled()
  })

  it('SEC-10 : un 429 bloque le champ et annonce le délai', async () => {
    const { screen } = await renderDialog(PASSWORD_ONLY, {
      reauthenticate: () =>
        Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 300 })),
    })

    await screen.getByLabelText('Mot de passe', { exact: true }).fill('essai')
    await screen.getByRole('button', { name: 'Confirmer' }).click()

    await expect.element(screen.getByText('Trop de tentatives')).toBeVisible()
    await expect.element(screen.getByText('5 minutes', { exact: false })).toBeVisible()
    await expect.element(screen.getByLabelText('Mot de passe', { exact: true })).toBeDisabled()
  })

  it('ADR 0035 : pendant l’envoi, le dialogue ne se ferme pas', async () => {
    let finish: () => void = () => undefined
    const { screen } = await renderDialog(PASSWORD_ONLY, {
      reauthenticate: () =>
        new Promise<void>((resolve) => {
          finish = resolve
        }),
    })

    await screen.getByLabelText('Mot de passe', { exact: true }).fill('phrase secrète')
    await screen.getByRole('button', { name: 'Confirmer' }).click()

    await expect.element(screen.getByRole('button', { name: 'Annuler' })).toBeDisabled()
    await expect.element(screen.getByRole('button', { name: 'Fermer' })).toBeDisabled()
    finish()
    await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument()
  })

  it('ADR 0035 : fermer efface la saisie et le refus ; la prochaine ouverture repart à vide', async () => {
    const { screen } = await renderDialog(PASSWORD_ONLY, {
      reauthenticate: () => Promise.reject(new ApiError(401, 'INVALID_CREDENTIALS')),
    })

    await screen.getByLabelText('Mot de passe', { exact: true }).fill('mauvais')
    await screen.getByRole('button', { name: 'Confirmer' }).click()
    await expect.element(screen.getByText('Mot de passe incorrect.')).toBeVisible()
    await screen.getByLabelText('Mot de passe', { exact: true }).fill('commencé')
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await screen.getByRole('button', { name: 'Rouvrir' }).click()

    await expect.element(screen.getByLabelText('Mot de passe', { exact: true })).toHaveValue('')
    expect(screen.getByText('Mot de passe incorrect.').elements()).toHaveLength(0)
  })

  it('AUTH-55, ADR 0034 : compte sans mot de passe, les seuls fournisseurs liés et configurés, vers leur départ', async () => {
    const { screen } = await renderDialog(
      { passwordSet: false, providers: ['google'] },
      { reauthenticate: () => Promise.reject(new Error('non attendu')) },
    )

    await expect
      .element(
        screen.getByText('Reconnectez-vous avec le fournisseur lié à votre compte pour continuer.'),
      )
      .toBeVisible()
    await expect
      .element(screen.getByRole('link', { name: 'Continuer avec Google' }))
      .toHaveAttribute('href', '/oauth2/authorization/google')
    expect(screen.getByRole('link', { name: 'Continuer avec GitHub' }).elements()).toHaveLength(0)
    expect(screen.getByLabelText('Mot de passe', { exact: true }).elements()).toHaveLength(0)
    expect(screen.getByRole('button', { name: 'Confirmer' }).elements()).toHaveLength(0)
  })

  it('AUTH-55 : un fournisseur lié mais plus configuré n’est pas proposé ; mot de passe et fournisseur : « ou » entre les deux', async () => {
    const { screen } = await renderDialog(
      { passwordSet: true, providers: ['google', 'github'] },
      { reauthenticate: () => Promise.resolve() },
      ['github'],
    )

    await expect.element(screen.getByLabelText('Mot de passe', { exact: true })).toBeVisible()
    await expect.element(screen.getByText('ou', { exact: true })).toBeVisible()
    await expect.element(screen.getByRole('link', { name: 'Continuer avec GitHub' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Continuer avec Google' }).elements()).toHaveLength(0)
  })

  it('CDC 3 : après la réussite comme après « Annuler », le mot de passe ne reste pas dans le cache des mutations', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { gcTime: 0 } },
    })
    const secrets = () =>
      JSON.stringify(
        client
          .getMutationCache()
          .getAll()
          .map((mutation) => mutation.state.variables),
      )
    let refuse = true
    const { screen } = await renderDialog(
      PASSWORD_ONLY,
      {
        reauthenticate: () =>
          refuse ? Promise.reject(new ApiError(401, 'INVALID_CREDENTIALS')) : Promise.resolve(),
      },
      ['google', 'github'],
      client,
    )

    await screen.getByLabelText('Mot de passe', { exact: true }).fill('presque bonne phrase')
    await screen.getByRole('button', { name: 'Confirmer' }).click()
    await expect.element(screen.getByText('Mot de passe incorrect.')).toBeVisible()
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await vi.waitFor(() => {
      expect(secrets()).not.toContain('presque bonne phrase')
    })

    refuse = false
    await screen.getByRole('button', { name: 'Rouvrir' }).click()
    await screen.getByLabelText('Mot de passe', { exact: true }).fill('phrase secrète')
    await screen.getByRole('button', { name: 'Confirmer' }).click()
    await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument()
    await vi.waitFor(() => {
      expect(secrets()).not.toContain('phrase secrète')
    })
  })

  it('AUTH-55, ADR 0035 : une lecture ratée des méthodes de connexion est refaite à l’ouverture', async () => {
    let calls = 0
    const { screen } = await renderDialog(
      () => {
        calls += 1
        return calls === 1 ? Promise.reject(new Error('réseau')) : Promise.resolve(PASSWORD_ONLY)
      },
      { reauthenticate: () => Promise.resolve() },
    )

    await expect.element(screen.getByLabelText('Mot de passe', { exact: true })).toBeVisible()
    expect(calls).toBe(2)
  })
})
