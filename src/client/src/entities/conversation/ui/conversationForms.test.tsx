import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import type { ConversationFailure } from '../model/conversationFailure'
import { ConversationAlert } from './ConversationAlert'
import { GroupNameField } from './GroupNameField'

function Field({ invalid = false }: { readonly invalid?: boolean }) {
  const [value, setValue] = useState('')
  return (
    <GroupNameField value={value} onChange={setValue} onBlur={() => undefined} invalid={invalid} />
  )
}

describe('entities/conversation : champ du nom et refus (ADR 0041, point 4)', () => {
  it('GRP-01, SEC-01 : « Nom du groupe », 128 unités au plus, sans placeholder', async () => {
    const screen = await render(<Field />)
    const input = screen.getByRole('textbox', { name: 'Nom du groupe' })

    await expect.element(input).toHaveAttribute('maxlength', '128')
    await expect.element(input).not.toHaveAttribute('placeholder')
  })

  it('GRP-01, ADR 0041 (point 4) : un nom refusé le dit sous le champ', async () => {
    const screen = await render(<Field invalid />)

    await expect
      .element(screen.getByText('1 à 64 caractères, dont un visible au moins.'))
      .toBeVisible()
  })

  it.each([
    [
      { kind: 'not-friends' },
      'Vous ne pouvez ajouter que vos amis. Leur liste vient d’être mise à jour.',
    ],
    [{ kind: 'group-full' }, 'Le groupe compte déjà 50 membres.'],
    [{ kind: 'forbidden' }, 'Vous n’êtes plus administrateur de ce groupe.'],
    [{ kind: 'gone' }, 'Cette conversation n’est plus disponible.'],
  ] as const)('ADR 0041 (point 4) : %o s’annonce en alerte', async (failure, text) => {
    const screen = await render(<ConversationAlert failure={failure} />)

    await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
  })

  it('ADR 0041 (point 4) : un nom refusé reste sous le champ ; aucun refus, aucune alerte', async () => {
    const invalidName: ConversationFailure = { kind: 'invalid-name' }
    const named = await render(<ConversationAlert failure={invalidName} />)
    expect(named.container.querySelector('[role="alert"]')).toBeNull()
    await named.unmount()

    const none = await render(<ConversationAlert failure={null} />)
    expect(none.container.querySelector('[role="alert"]')).toBeNull()
  })

  it('ADR 0041 (point 4) : une panne réseau reprend l’alerte commune des formulaires', async () => {
    const screen = await render(<ConversationAlert failure={{ kind: 'network' }} />)

    await expect.element(screen.getByRole('alert')).toBeVisible()
  })
})
