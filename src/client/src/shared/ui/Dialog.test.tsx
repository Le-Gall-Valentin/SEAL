import { userEvent } from 'vitest/browser'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { Dialog } from './Dialog'

describe('ADR 0029 : fenêtre de dialogue de la maquette (Dialogue-Cle-Recuperation)', () => {
  it('KEY-30 : fenêtre modale nommée par son titre, qui reçoit le focus et que Échap ne ferme pas', async () => {
    const screen = await render(
      <Dialog labelledBy="titre" describedBy="texte">
        <h2 id="titre">Votre clé de récupération</h2>
        <p id="texte">Elle seule permet de restaurer vos clés.</p>
      </Dialog>,
    )
    const dialog = screen.getByRole('dialog', { name: 'Votre clé de récupération' })

    await expect.element(dialog).toHaveAttribute('aria-modal', 'true')
    await expect
      .element(dialog)
      .toHaveAccessibleDescription('Elle seule permet de restaurer vos clés.')
    await expect.element(dialog).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    await expect.element(dialog).toBeInTheDocument()
  })
})
