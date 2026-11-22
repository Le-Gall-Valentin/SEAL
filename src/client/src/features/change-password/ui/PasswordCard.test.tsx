import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { PasswordBlocklistProvider } from '@/entities/password'
import type { Account } from '@/entities/session'
import { ApiError } from '@/shared/lib'
import { PasswordChangeApiProvider } from '../model/passwordChangeApiPort'
import { PasswordCard } from './PasswordCard'

const ACCOUNT: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Valentin Dupont',
  email: 'valentin@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}
const STRONG = 'raquette-sous-la-neige'

interface PasswordChange {
  readonly currentPassword: string
  readonly newPassword: string
  readonly signOutOtherSessions: boolean
}

async function renderCard(changePassword: (change: PasswordChange) => Promise<void>) {
  const screen = await render(
    <QueryClientProvider client={new QueryClient()}>
      <PasswordBlocklistProvider api={{ load: () => Promise.resolve(['motdepassetropcourant']) }}>
        <PasswordChangeApiProvider api={{ changePassword }}>
          <PasswordCard account={ACCOUNT} />
        </PasswordChangeApiProvider>
      </PasswordBlocklistProvider>
    </QueryClientProvider>,
  )
  await screen.getByRole('button', { name: 'Changer le mot de passe' }).click()
  return screen
}

describe('ACC-01 : « Changer le mot de passe »', () => {
  it('ACC-01 : mots de passe et option cochée par défaut partent ; le dialogue se ferme et la réussite est annoncée', async () => {
    const sent: PasswordChange[] = []
    const screen = await renderCard((change) => {
      sent.push(change)
      return Promise.resolve()
    })

    const dialog = screen.getByRole('dialog', { name: 'Changer le mot de passe' })
    const signOut = dialog.getByRole('switch', { name: 'Déconnecter mes autres sessions' })
    await expect.element(signOut).toHaveAttribute('aria-checked', 'true')
    const submit = dialog.getByRole('button', { name: 'Changer le mot de passe' })
    await expect.element(submit).toBeDisabled()
    await dialog.getByLabelText('Mot de passe actuel').fill('ancienne phrase')
    await dialog.getByLabelText('Nouveau mot de passe').fill(STRONG)
    await submit.click()

    await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument()
    await expect.element(screen.getByRole('status')).toHaveTextContent('Mot de passe changé.')
    expect(sent).toEqual([
      { currentPassword: 'ancienne phrase', newPassword: STRONG, signOutOtherSessions: true },
    ])
  })

  it('ACC-01, ADR 0034 : l’option décochée part décochée', async () => {
    const sent: PasswordChange[] = []
    const screen = await renderCard((change) => {
      sent.push(change)
      return Promise.resolve()
    })

    await screen.getByRole('switch', { name: 'Déconnecter mes autres sessions' }).click()
    await screen.getByLabelText('Mot de passe actuel').fill('ancienne phrase')
    await screen.getByLabelText('Nouveau mot de passe').fill(STRONG)
    await screen
      .getByRole('dialog')
      .getByRole('button', { name: 'Changer le mot de passe' })
      .click()

    await expect.element(screen.getByRole('status')).toHaveTextContent('Mot de passe changé.')
    expect(sent.map((change) => change.signOutOtherSessions)).toEqual([false])
  })

  it('AUTH-11 : un nouveau mot de passe trop court laisse l’envoi inactif ; les règles le disent', async () => {
    const screen = await renderCard(() => Promise.reject(new Error('non attendu')))

    await screen.getByLabelText('Mot de passe actuel').fill('ancienne phrase')
    await screen.getByLabelText('Nouveau mot de passe').fill('court')

    await expect
      .element(screen.getByRole('dialog').getByRole('button', { name: 'Changer le mot de passe' }))
      .toBeDisabled()
  })

  it('ACC-01 : mot de passe actuel refusé, « Mot de passe actuel incorrect. » sous le champ vidé ; le dialogue reste', async () => {
    const screen = await renderCard(() => Promise.reject(new ApiError(401, 'INVALID_CREDENTIALS')))

    const current = screen.getByLabelText('Mot de passe actuel')
    await current.fill('mauvaise phrase')
    await screen.getByLabelText('Nouveau mot de passe').fill(STRONG)
    await screen
      .getByRole('dialog')
      .getByRole('button', { name: 'Changer le mot de passe' })
      .click()

    await expect.element(screen.getByText('Mot de passe actuel incorrect.')).toBeVisible()
    await expect.element(current).toHaveValue('')
    await expect.element(screen.getByRole('dialog')).toBeVisible()
  })

  it('AUTH-11 : un nouveau mot de passe refusé par le serveur (422) garde l’envoi inactif tant qu’il reste saisi', async () => {
    const screen = await renderCard(() => Promise.reject(new ApiError(422, 'PASSWORD_TOO_WEAK')))

    await screen.getByLabelText('Mot de passe actuel').fill('ancienne phrase')
    await screen.getByLabelText('Nouveau mot de passe').fill(STRONG)
    const submit = screen
      .getByRole('dialog')
      .getByRole('button', { name: 'Changer le mot de passe' })
    await submit.click()

    await expect.element(submit).toBeDisabled()
    await screen.getByLabelText('Nouveau mot de passe').fill(`${STRONG}-bis`)
    await expect.element(submit).toBeEnabled()
  })

  it('ADR 0035 : fermer efface les deux mots de passe et remet l’option cochée', async () => {
    const screen = await renderCard(() => Promise.reject(new Error('non attendu')))

    await screen.getByLabelText('Mot de passe actuel').fill('ancienne phrase')
    await screen.getByLabelText('Nouveau mot de passe').fill(STRONG)
    await screen.getByRole('switch', { name: 'Déconnecter mes autres sessions' }).click()
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await screen.getByRole('button', { name: 'Changer le mot de passe' }).click()

    await expect.element(screen.getByLabelText('Mot de passe actuel')).toHaveValue('')
    await expect.element(screen.getByLabelText('Nouveau mot de passe')).toHaveValue('')
    await expect
      .element(screen.getByRole('switch', { name: 'Déconnecter mes autres sessions' }))
      .toHaveAttribute('aria-checked', 'true')
  })
})
