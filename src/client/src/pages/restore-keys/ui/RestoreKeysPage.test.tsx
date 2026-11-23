import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { LogoutApiProvider } from '@/features/logout'
import { RestoreKeysPage } from './RestoreKeysPage'

const { retry } = vi.hoisted(() => ({ retry: vi.fn() }))

vi.mock('@/features/key-restore', () => ({
  KeyRestoreForm: ({ owner }: { readonly owner: { readonly username: string } }) => (
    <p>formulaire de {owner.username}</p>
  ),
}))

// Dialogue-Cle-Recuperation : sa logique a ses propres tests ; ici, ce que la page lui passe et ce qu'elle en fait.
vi.mock('@/features/key-setup', () => ({
  KeySetupDialog: (props: {
    readonly variant?: 'setup' | 'reset'
    readonly onDone: () => void
    readonly onReauthRequired?: (again: () => void) => void
  }) => (
    <div role="dialog" aria-label="Votre clé de récupération">
      <p>mode {props.variant ?? 'setup'}</p>
      <button type="button" onClick={() => props.onReauthRequired?.(retry)}>
        mode sudo fermé
      </button>
      <button type="button" onClick={props.onDone}>
        terminé
      </button>
    </div>
  ),
}))

vi.mock('@/features/reauthenticate', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/reauthenticate')>()),
  ReauthDialog: (props: {
    readonly open: boolean
    readonly onReauthenticated: () => void
    readonly onCancel: () => void
  }) =>
    props.open ? (
      <div role="dialog" aria-label="Confirmez votre identité">
        <button type="button" onClick={props.onReauthenticated}>
          confirmée
        </button>
      </div>
    ) : null,
}))

const OWNER = { userId: '0192f7a1-ffff-7000-8000-00000000a11c', username: 'valentin.dupont' }

function Location() {
  return <p>adresse : {useLocation().search}</p>
}

async function renderPage(onRestored: () => void = () => undefined, entry = '/parametres') {
  const sessionApi: ISessionApi = {
    me: () => Promise.resolve(null),
    reloadCsrf: () => Promise.resolve(),
  }
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionApiProvider api={sessionApi}>
        <LogoutApiProvider api={{ logout: () => Promise.resolve() }}>
          <MemoryRouter initialEntries={[entry]}>
            <RestoreKeysPage owner={OWNER} onRestored={onRestored} />
            <Location />
          </MemoryRouter>
        </LogoutApiProvider>
      </SessionApiProvider>
    </QueryClientProvider>,
  )
}

describe('KEY-33, KEY-34 : page Restauration-Appareil', () => {
  it('KEY-33, KEY-34 : textes de la maquette, le formulaire du compte, « Se déconnecter » et « Clé de récupération perdue ? »', async () => {
    const screen = await renderPage()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Restaurer vos clés' }))
      .toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Premier passage sur cet appareil : saisissez votre clé de récupération. Votre identité reste la même, vos contacts ne verront aucune alerte.',
        ),
      )
      .toBeVisible()
    await expect.element(screen.getByText('formulaire de valentin.dupont')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
    await expect.element(screen.getByText('Clé de récupération perdue ?')).toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Réinitialiser votre identité crée de nouvelles clés : vos anciens messages deviendront illisibles et vos contacts verront que votre clé a changé.',
        ),
      )
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Réinitialiser mon identité…' }))
      .toBeVisible()
  })

  it('KEY-34, ADR 0036 (point 3) : la confirmation annonce l’historique illisible ; « Annuler » ne lance rien', async () => {
    const screen = await renderPage()

    await screen.getByRole('button', { name: 'Réinitialiser mon identité…' }).click()

    const confirm = screen.getByRole('alertdialog', { name: 'Réinitialiser votre identité ?' })
    await expect.element(confirm.getByText('Cette action est irréversible.')).toBeVisible()
    await expect
      .element(
        confirm.getByText(
          'Vos anciens messages deviendront illisibles et vos contacts verront que votre clé a changé. Une nouvelle clé de récupération vous sera remise.',
        ),
      )
      .toBeVisible()
    await confirm.getByRole('button', { name: 'Annuler' }).click()

    await expect.element(confirm).not.toBeInTheDocument()
    expect(screen.getByText('mode reset').query()).toBeNull()
  })

  it('KEY-34, AUTH-55 : « Réinitialiser » ouvre la clé en mode reset ; un 403 ouvre la ré-authentification, puis la relance ; la fin relit les clés', async () => {
    const onRestored = vi.fn()
    const screen = await renderPage(onRestored)
    await screen.getByRole('button', { name: 'Réinitialiser mon identité…' }).click()

    await screen.getByRole('button', { name: 'Réinitialiser', exact: true }).click()

    await expect
      .element(screen.getByRole('alertdialog', { name: 'Réinitialiser votre identité ?' }))
      .not.toBeInTheDocument()
    await expect.element(screen.getByText('mode reset')).toBeVisible()
    // ADR 0036 (point 3) : Dialogue-Cle-Recuperation seul sur le fond, comme à la première connexion (ADR 0029).
    expect(screen.getByRole('heading', { name: 'Restaurer vos clés' }).query()).toBeNull()
    expect(screen.getByText('formulaire de valentin.dupont').query()).toBeNull()
    await screen.getByRole('button', { name: 'mode sudo fermé' }).click()
    await screen.getByRole('button', { name: 'confirmée' }).click()
    await expect
      .element(screen.getByRole('dialog', { name: 'Confirmez votre identité' }))
      .not.toBeInTheDocument()
    expect(retry).toHaveBeenCalledTimes(1)
    await screen.getByRole('button', { name: 'terminé' }).click()
    expect(onRestored).toHaveBeenCalledTimes(1)
  })

  it('AUTH-55, ADR 0036 (point 4) : un retour OAuth refusé s’annonce sur la restauration, puis quitte l’adresse', async () => {
    const screen = await renderPage(undefined, '/parametres?erreur=REQUEST_REJECTED')

    await expect
      .element(screen.getByText('La confirmation par le fournisseur a échoué. Réessayez.'))
      .toBeVisible()
    await expect.element(screen.getByText('adresse :', { exact: true })).toBeVisible()
  })
})
