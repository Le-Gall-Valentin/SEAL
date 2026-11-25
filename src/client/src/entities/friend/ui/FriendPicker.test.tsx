import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import { isolate } from '@/shared/lib'
import type { Friend } from '../model/friendPicker'
import { FriendPicker } from './FriendPicker'

let next = 0
function friend(username: string, displayName: string): Friend {
  next += 1
  return {
    userId: `0192f7a1-0000-7000-8000-${String(next).padStart(12, '0')}`,
    username,
    displayName,
    friendsSince: new Date(0),
    lastInteractionAt: null,
    interactionCount30d: 0,
  }
}

const MATHIS = friend('mathis_b', 'Mathis Bouchard')
const MAUDE = friend('maude.leblanc', 'Maude Leblanc')
const THOMAS = friend('thomas.lavoie', 'Thomas Lavoie')
const CAMILLE = friend('camille.t', 'Camille Tremblay')
const FRIENDS = [MATHIS, MAUDE, THOMAS, CAMILLE]

function Picker({
  friends = FRIENDS,
  exclude = new Set<string>(),
  max = 49,
  onSelect = () => undefined,
}: {
  readonly friends?: readonly Friend[]
  readonly exclude?: ReadonlySet<string>
  readonly max?: number
  readonly onSelect?: (friend: Friend) => void
}) {
  const [selected, setSelected] = useState<readonly Friend[]>([])
  return (
    <FriendPicker
      label="Membres"
      friends={friends}
      selected={selected}
      exclude={exclude}
      max={max}
      onSelect={(chosen) => {
        onSelect(chosen)
        setSelected((current) => [...current, chosen])
      }}
      onRemove={(removed) => {
        setSelected((current) => current.filter((f) => f.userId !== removed.userId))
      }}
    />
  )
}

describe('entities/friend : sélecteur d’amis au clavier (FRD-20)', () => {
  it('FRD-20 : la saisie classe les amis et surligne la correspondance ; Entrée ajoute et vide la saisie', async () => {
    const onSelect = vi.fn()
    const screen = await render(<Picker onSelect={onSelect} />)
    const input = screen.getByRole('combobox', { name: 'Membres' })

    await input.fill('ma')

    await expect.element(screen.getByRole('option', { name: /Thomas Lavoie/u })).toBeVisible()
    const shown = screen
      .getByRole('option')
      .all()
      .map((option) => option.element().textContent)
    expect(shown).toEqual([
      'MBMathis Bouchard@mathis_b',
      'MLMaude Leblanc@maude.leblanc',
      'TLThomas Lavoie@thomas.lavoie',
    ])
    expect(screen.container.querySelector('[role="option"] mark')?.textContent).toBe('Ma')
    await userEvent.keyboard('{Enter}')

    expect(onSelect).toHaveBeenCalledWith(MATHIS)
    await expect.element(input).toHaveValue('')
    await expect
      .element(screen.getByRole('button', { name: `Retirer ${isolate('Mathis Bouchard')}` }))
      .toBeVisible()
    await expect.element(screen.getByRole('option', { name: /Mathis/u })).not.toBeInTheDocument()
  })

  it('FRD-20 : ↓ et ↑ déplacent l’option active, que porte aria-activedescendant', async () => {
    const onSelect = vi.fn()
    const screen = await render(<Picker onSelect={onSelect} />)
    const input = screen.getByRole('combobox', { name: 'Membres' })

    await input.fill('ma')
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{ArrowUp}{ArrowDown}')

    const active = input.element().getAttribute('aria-activedescendant') ?? ''
    expect(document.getElementById(active)?.textContent).toContain('@thomas.lavoie')
    await userEvent.keyboard('{Enter}')
    expect(onSelect).toHaveBeenCalledWith(THOMAS)
  })

  it('FRD-20, ADR 0041 : Échap vide la saisie, puis ferme la liste, sans fermer le dialogue ; le suivant le ferme', async () => {
    const screen = await render(
      <dialog>
        <Picker />
      </dialog>,
    )
    const dialog = screen.container.querySelector('dialog')
    dialog?.showModal()
    const input = screen.getByRole('combobox', { name: 'Membres' })

    await input.fill('ma')
    await userEvent.keyboard('{Escape}')
    await expect.element(input).toHaveValue('')
    expect(dialog?.open).toBe(true)
    await expect.element(screen.getByRole('listbox', { name: 'Ajouter des amis' })).toBeVisible()

    await userEvent.keyboard('{Escape}')
    await expect.element(screen.getByRole('listbox')).not.toBeInTheDocument()
    expect(dialog?.open).toBe(true)

    await userEvent.keyboard('{Escape}')
    expect(dialog?.open).toBe(false)
  })

  it('FRD-20 : sans saisie ni liste affichée, Échap n’est pas retenu et ferme le dialogue au premier appui', async () => {
    const screen = await render(
      <dialog>
        <Picker friends={[]} />
      </dialog>,
    )
    const dialog = screen.container.querySelector('dialog')
    dialog?.showModal()
    await screen.getByRole('combobox', { name: 'Membres' }).click()

    await userEvent.keyboard('{Escape}')

    expect(dialog?.open).toBe(false)
  })

  it('FRD-20 : aria-controls ne désigne la liste que lorsqu’elle est affichée', async () => {
    const screen = await render(<Picker />)
    const input = screen.getByRole('combobox', { name: 'Membres' })
    await expect.element(input).toHaveAttribute('aria-controls')

    await input.fill('zz')

    await expect.element(input).not.toHaveAttribute('aria-controls')
  })

  it('FRD-20 : un clic sur une option l’ajoute ; « Retirer » la rend à la liste', async () => {
    const screen = await render(<Picker />)

    await screen.getByRole('option', { name: /Camille Tremblay/u }).click()
    await screen.getByRole('button', { name: `Retirer ${isolate('Camille Tremblay')}` }).click()

    await expect.element(screen.getByRole('option', { name: /Camille Tremblay/u })).toBeVisible()
  })

  it('FRD-20 (étape 2) : les membres déjà présents ne sont pas proposés', async () => {
    const screen = await render(<Picker exclude={new Set([MAUDE.userId])} />)

    await expect.element(screen.getByRole('option', { name: /Mathis/u })).toBeVisible()
    await expect.element(screen.getByRole('option', { name: /Maude/u })).not.toBeInTheDocument()
  })

  it('GRP-01, WCAG 2.4.3 : la sélection pleine met la saisie en lecture seule, focus gardé, et retire la liste', async () => {
    const screen = await render(<Picker max={1} />)
    const input = screen.getByRole('combobox', { name: 'Membres' })

    await input.fill('ma')
    await userEvent.keyboard('{Enter}')

    await expect.element(input).toHaveFocus()
    await expect.element(input).toHaveAttribute('readonly')
    await expect.element(input).toHaveAttribute('aria-disabled', 'true')
    await expect.element(screen.getByRole('listbox')).not.toBeInTheDocument()
  })

  it('FRD-20 : sans liste, Entrée n’est pas retenue et envoie le formulaire', async () => {
    const onSubmit = vi.fn((event: Event) => {
      event.preventDefault()
    })
    const screen = await render(
      <form
        onSubmit={(event) => {
          onSubmit(event.nativeEvent)
        }}
      >
        <Picker max={1} />
        <button type="submit">OK</button>
      </form>,
    )

    await screen.getByRole('combobox', { name: 'Membres' }).fill('ma')
    await userEvent.keyboard('{Enter}')
    await userEvent.keyboard('{Enter}')

    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('FRD-20, WCAG 2.4.3 : retirer un jeton rend le focus à la saisie', async () => {
    const screen = await render(<Picker />)

    await screen.getByRole('option', { name: /Camille Tremblay/u }).click()
    await screen.getByRole('button', { name: `Retirer ${isolate('Camille Tremblay')}` }).click()

    await expect.element(screen.getByRole('combobox', { name: 'Membres' })).toHaveFocus()
  })

  it('FRD-20, ADR 0041 (point 4) : sans ami, puis sans correspondance, le dire', async () => {
    const empty = await render(<Picker friends={[]} />)
    await expect.element(empty.getByText('Vous n’avez pas encore d’amis.')).toBeVisible()
    await empty.unmount()

    const screen = await render(<Picker />)
    // Région live présente dès l'affichage, vide : son remplissage est annoncé par les lecteurs d'écran.
    const status = screen.getByRole('status')
    await expect.element(status).toBeInTheDocument()
    expect(status.element().textContent).toBe('')
    await screen.getByRole('combobox', { name: 'Membres' }).fill('zz')
    await expect
      .element(status)
      .toHaveTextContent(`Aucun ami ne correspond à « ${isolate('zz')} ».`)
  })

  it('GRP-03, ADR 0041 (point 4) : tous les amis déjà membres, le dire au lieu d’une liste vide', async () => {
    const screen = await render(
      <Picker exclude={new Set(FRIENDS.map((candidate) => candidate.userId))} />,
    )

    await expect.element(screen.getByText('Tous vos amis sont déjà membres.')).toBeVisible()
    await expect.element(screen.getByRole('listbox')).not.toBeInTheDocument()
    await expect.element(screen.getByText('Vous n’avez pas encore d’amis.')).not.toBeInTheDocument()
  })

  it('ACC-03 : un nom chargé de marques combinantes reste dans son jeton', async () => {
    const zalgo = friend('zalgo', `Z${'\u0301'.repeat(60)}`)
    const screen = await render(<Picker friends={[zalgo]} />)
    await screen.getByRole('option').click()

    const remove = screen.getByRole('button', { name: /^Retirer/u }).element()
    const name = remove.previousElementSibling
    expect(name && getComputedStyle(name).overflowY).toBe('hidden')
  })

  it('FRD-20 : aides de la maquette Nouvelle-Conversation', async () => {
    const screen = await render(<Picker />)

    await expect.element(screen.getByText('Seuls vos amis apparaissent ici.')).toBeVisible()
    await expect
      .element(screen.getByText('↑ ↓ pour choisir · Entrée pour ajouter · Échap pour effacer'))
      .toBeVisible()
    await expect.element(screen.getByText('Meilleures correspondances')).toBeVisible()
  })

  it('FRD-20, A10 : pendant le chargement, rien ne dit « pas encore d’amis » ; un échec propose de réessayer', async () => {
    const loading = await render(
      <FriendPicker
        label="Membres"
        friends={[]}
        loading
        selected={[]}
        exclude={new Set()}
        max={49}
        onSelect={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    await expect
      .element(loading.getByText('Vous n’avez pas encore d’amis.'))
      .not.toBeInTheDocument()
    await loading.unmount()

    const onRetry = vi.fn()
    const failed = await render(
      <FriendPicker
        label="Membres"
        friends={[]}
        failed
        onRetry={onRetry}
        selected={[]}
        exclude={new Set()}
        max={49}
        onSelect={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    await expect.element(failed.getByText('Vous n’avez pas encore d’amis.')).not.toBeInTheDocument()
    await failed.getByRole('button', { name: 'Réessayer' }).click()
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
