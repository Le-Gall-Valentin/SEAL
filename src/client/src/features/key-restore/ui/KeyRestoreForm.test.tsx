import { describe, expect, it, vi } from 'vitest'
import { userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { type KeyRestore, useKeyRestore } from '../model/useKeyRestore'
import { KeyRestoreForm } from './KeyRestoreForm'

vi.mock('../model/useKeyRestore', () => ({ useKeyRestore: vi.fn() }))

const OWNER = { userId: '0192f7a1-ffff-7000-8000-00000000a11c', username: 'valentin.dupont' }

function state(overrides: Partial<KeyRestore> = {}): KeyRestore {
  return {
    groups: ['', '', '', '', '', '', '', ''],
    enter: vi.fn(() => 0),
    canSubmit: false,
    submit: vi.fn(),
    pending: false,
    failure: null,
    ...overrides,
  }
}

async function renderWith(restore: KeyRestore) {
  vi.mocked(useKeyRestore).mockReturnValue(restore)
  return render(<KeyRestoreForm owner={OWNER} onRestored={() => undefined} />)
}

describe('KEY-33 : Restauration-Appareil, formulaire', () => {
  it('KEY-33 : 8 cases nommées dans le groupe « Clé de récupération », l’aide et la note de la maquette', async () => {
    const screen = await renderWith(state())
    const group = screen.getByRole('group', { name: 'Clé de récupération' })

    await expect
      .element(group)
      .toHaveAccessibleDescription('32 caractères en 8 groupes de 4, sans I, L, O ni U.')
    for (let index = 1; index <= 8; index += 1) {
      await expect
        .element(screen.getByLabelText(`Groupe ${String(index)} sur 8`))
        .toHaveAttribute('autocomplete', 'off')
    }
    await expect
      .element(
        screen.getByText(
          'Le déchiffrement se fait ici : votre clé n’est jamais envoyée au serveur.',
        ),
      )
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Restaurer mes clés' })).toBeDisabled()
  })

  it('KEY-33 : une saisie passe au modèle, et le focus va à la case qu’il désigne', async () => {
    const restore = state({ enter: vi.fn(() => 1) })
    const screen = await renderWith(restore)

    await screen.getByLabelText('Groupe 1 sur 8').fill('8wqk')

    expect(restore.enter).toHaveBeenLastCalledWith(0, '8wqk')
    await expect.element(screen.getByLabelText('Groupe 2 sur 8')).toHaveFocus()
  })

  it('KEY-33 : Retour arrière dans une case vide revient à la précédente, sans rien effacer ; ailleurs, il efface', async () => {
    const restore = state({ groups: ['8WQK', '', '', '', '', '', '', ''] })
    const screen = await renderWith(restore)
    const second = screen.getByLabelText('Groupe 2 sur 8')

    await second.click()
    await userEvent.keyboard('{Backspace}')

    await expect.element(screen.getByLabelText('Groupe 1 sur 8')).toHaveFocus()
    expect(restore.enter).not.toHaveBeenCalled()
    await screen.getByLabelText('Groupe 1 sur 8').click()
    await userEvent.keyboard('{End}{Backspace}')
    expect(restore.enter).toHaveBeenLastCalledWith(0, '8WQ')
    await expect.element(screen.getByLabelText('Groupe 1 sur 8')).toHaveFocus()
  })

  it('KEY-33 : clé complète, l’envoi lance la restauration ; en cours, le bouton est inactif', async () => {
    const restore = state({ canSubmit: true })
    const screen = await renderWith(restore)

    await screen.getByRole('button', { name: 'Restaurer mes clés' }).click()
    expect(restore.submit).toHaveBeenCalledOnce()
    await screen.unmount()

    const pending = await renderWith(state({ canSubmit: false, pending: true }))
    await expect.element(pending.getByRole('button', { name: 'Restaurer mes clés' })).toBeDisabled()
  })

  it('ADR 0029, point 5 : chaque refus s’annonce avec son texte', async () => {
    for (const [kind, text] of [
      ['wrong-key', 'Cette clé de récupération ne correspond pas à votre coffre.'],
      [
        'invalid',
        'Le coffre reçu du serveur est invalide : vos clés n’ont pas été restaurées. Prévenez l’administrateur du serveur.',
      ],
      ['network', 'Connexion impossible. Vérifiez votre réseau puis réessayez.'],
      ['unexpected', 'Une erreur inattendue est survenue. Réessayez.'],
    ] as const) {
      const screen = await renderWith(state({ failure: { kind } }))
      await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
      await screen.unmount()
    }
  })
})
