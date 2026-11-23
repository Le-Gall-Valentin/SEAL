import { useState } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { page, userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { DialogBody, DialogFooter, ModalDialog } from './ModalDialog'

function Harness({
  closable = true,
  tall = false,
  danger = false,
}: {
  readonly closable?: boolean
  readonly tall?: boolean
  readonly danger?: boolean
}) {
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
        danger={danger}
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
            {tall && Array.from({ length: 30 }, (_, line) => <p key={line}>Ligne {line + 1}</p>)}
          </DialogBody>
          <DialogFooter
            onCancel={close}
            cancelable={closable}
            submitLabel="Envoyer le code"
            canSubmit
            danger={danger}
          />
        </form>
      </ModalDialog>
    </>
  )
}

describe('ACC-01, ACC-02, ADR 0035 (point 1) : fenêtre modale des paramètres', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('ACC-02, ADR 0035 : à l’ouverture, le focus va au premier champ', async () => {
    const screen = await render(<Harness />)
    await screen.getByRole('button', { name: 'Ouvrir' }).click()

    await expect.element(screen.getByLabelText('Champ')).toHaveFocus()
  })

  it('ACC-01, ADR 0024 (point 11) : plus haute que la fenêtre (téléphone en paysage), elle défile jusqu’à ses boutons', async () => {
    await page.viewport(844, 390)
    const screen = await render(<Harness tall />)
    await screen.getByRole('button', { name: 'Ouvrir' }).click()

    const dialog = screen.getByRole('dialog').element()
    expect(dialog.scrollHeight).toBeGreaterThan(dialog.clientHeight)
    // Défilement possible à la main (molette, doigt), pas seulement par programme.
    expect(['auto', 'scroll']).toContain(getComputedStyle(dialog).overflowY)
    const submit = screen.getByRole('button', { name: 'Envoyer le code' })
    submit.element().scrollIntoView()
    const box = submit.element().getBoundingClientRect()
    expect(box.bottom).toBeLessThanOrEqual(390)
    expect(box.top).toBeGreaterThanOrEqual(0)
  })

  it('KEY-34, ADR 0036 (point 3) : une action irréversible met le sous-titre et le bouton d’envoi en --danger', async () => {
    /** Couleur d'un jeton de design (#rrggbb), telle que getComputedStyle la rend. */
    const token = (name: string) => {
      const hex = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
      const [r, g, b] = [1, 3, 5].map((start) => Number.parseInt(hex.slice(start, start + 2), 16))
      return `rgb(${String(r)}, ${String(g)}, ${String(b)})`
    }
    const colors = async (danger: boolean) => {
      const screen = await render(<Harness danger={danger} />)
      await screen.getByRole('button', { name: 'Ouvrir' }).click()
      const subtitle = getComputedStyle(screen.getByText('Cette action est protégée.').element())
      const submit = getComputedStyle(
        screen.getByRole('button', { name: 'Envoyer le code' }).element(),
      )
      const result = [subtitle.color, submit.backgroundColor, submit.color]
      await screen.unmount()
      return result
    }

    expect(await colors(true)).toEqual([token('--danger'), token('--danger'), token('--on-danger')])
    expect(await colors(false)).toEqual([token('--ink-3'), token('--accent'), token('--on-accent')])
  })

  it('ACC-02, ADR 0035 : titre, étape et sous-titre ; le reste de la page devient inerte', async () => {
    const screen = await render(<Harness />)
    await screen.getByRole('button', { name: 'Ouvrir' }).click()

    const dialog = screen.getByRole('dialog', { name: 'Changer de courriel' })
    await expect.element(dialog).toBeVisible()
    await expect.element(dialog.getByText('Étape 1 sur 2')).toBeVisible()
    await expect.element(dialog.getByText('Cette action est protégée.')).toBeVisible()
    // Ouverte par showModal : le navigateur rend inerte tout ce qui est hors de la fenêtre.
    expect(dialog.element().matches(':modal')).toBe(true)
  })

  it('ACC-01, ADR 0035 : « Fermer », « Annuler » et Échap ferment ; le focus revient au bouton d’origine', async () => {
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

  it('ACC-01, ADR 0035 : pendant un envoi, ni « Fermer », ni « Annuler », ni Échap ne ferment la fenêtre', async () => {
    const screen = await render(<Harness closable={false} />)
    await screen.getByRole('button', { name: 'Ouvrir' }).click()

    await expect.element(screen.getByRole('button', { name: 'Fermer' })).toBeDisabled()
    await expect.element(screen.getByRole('button', { name: 'Annuler' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    await expect.element(screen.getByRole('dialog')).toBeVisible()
    await expect.element(screen.getByText('Fermetures : 0')).toBeInTheDocument()
  })
})
