import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { DialogBody, DialogFooter, ModalDialog } from './ModalDialog'

function Harness({ closable = true }: { readonly closable?: boolean }) {
  const [open, setOpen] = useState(false)
  const [closed, setClosed] = useState(0)
  const close = () => {
    setOpen(false)
    setClosed((count) => count + 1)
  }
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setOpen(true)
        }}
      >
        Ouvrir
      </button>
      <button type="button">Derrière</button>
      <p>Fermetures : {closed}</p>
      <ModalDialog
        open={open}
        onClose={close}
        closable={closable}
        title="Changer de courriel"
        eyebrow="Étape 1 sur 2"
        subtitle="Cette action est protégée."
      >
        <form
          onSubmit={(event) => {
            event.preventDefault()
          }}
        >
          <DialogBody>
            <label>
              Champ
              <input />
            </label>
          </DialogBody>
          <DialogFooter
            onCancel={close}
            cancelable={closable}
            submitLabel="Envoyer le code"
            canSubmit
          />
        </form>
      </ModalDialog>
    </>
  )
}

describe('ADR 0035, point 1 : fenêtre modale des paramètres', () => {
  it('ADR 0035 : titre, étape et sous-titre ; le reste de la page devient inerte', async () => {
    const screen = await render(<Harness />)
    await screen.getByRole('button', { name: 'Ouvrir' }).click()

    const dialog = screen.getByRole('dialog', { name: 'Changer de courriel' })
    await expect.element(dialog).toBeVisible()
    await expect.element(dialog.getByText('Étape 1 sur 2')).toBeVisible()
    await expect.element(dialog.getByText('Cette action est protégée.')).toBeVisible()
    // Ouverte par showModal : le navigateur rend inerte tout ce qui est hors de la fenêtre.
    expect(dialog.element().matches(':modal')).toBe(true)
  })

  it('ADR 0035 : « Fermer », « Annuler » et Échap ferment ; le focus revient au bouton d’origine', async () => {
    const screen = await render(<Harness />)
    const opener = screen.getByRole('button', { name: 'Ouvrir' })

    await opener.click()
    await screen.getByRole('button', { name: 'Fermer' }).click()
    await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument()
    await expect.element(opener).toHaveFocus()

    await opener.click()
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await expect.element(screen.getByText('Fermetures : 2')).toBeVisible()

    await opener.click()
    await userEvent.keyboard('{Escape}')
    await expect.element(screen.getByText('Fermetures : 3')).toBeVisible()
    await expect.element(opener).toHaveFocus()
  })

  it('ADR 0035 : pendant un envoi, ni « Fermer », ni « Annuler », ni Échap ne ferment la fenêtre', async () => {
    const screen = await render(<Harness closable={false} />)
    await screen.getByRole('button', { name: 'Ouvrir' }).click()

    await expect.element(screen.getByRole('button', { name: 'Fermer' })).toBeDisabled()
    await expect.element(screen.getByRole('button', { name: 'Annuler' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    await expect.element(screen.getByRole('dialog')).toBeVisible()
    await expect.element(screen.getByText('Fermetures : 0')).toBeInTheDocument()
  })
})
