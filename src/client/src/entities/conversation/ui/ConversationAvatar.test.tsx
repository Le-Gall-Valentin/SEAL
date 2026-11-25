import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Conversation, Member } from '../model/conversations'
import { ConversationAvatar } from './ConversationAvatar'

const ME = '0192f7a1-0000-7000-8000-000000000001'
let next = 1
const member = (displayName: string): Member => {
  next += 1
  return {
    userId: `0192f7a1-0000-7000-8000-${String(next).padStart(12, '0')}`,
    username: `u${String(next)}`,
    displayName,
    role: 'MEMBER',
  }
}
const me: Member = { userId: ME, username: 'moi', displayName: 'Moi Même', role: 'ADMIN' }

function group(members: readonly Member[], name = 'Équipe 8INF138'): Conversation {
  return { id: 'c', type: 'GROUP', name, members, createdAt: new Date(0), lastServerSeq: 0 }
}

const letters = (container: HTMLElement) =>
  Array.from(container.querySelectorAll('[aria-hidden="true"] span')).map(
    (cell) => cell.textContent,
  )

describe('entities/conversation : avatar d’une conversation (maquette Conv-Ordinateur)', () => {
  it('CNV-04 : une directe montre les initiales de l’interlocuteur', async () => {
    const direct: Conversation = {
      ...group([me, member('Mathis Bouchard')]),
      type: 'DIRECT',
      name: null,
    }
    const screen = await render(<ConversationAvatar conversation={direct} me={ME} size={44} />)

    await expect.element(screen.getByText('MB')).toBeInTheDocument()
  })

  it('CNV-04 : un groupe montre la mosaïque des trois premiers autres membres, une lettre chacun', async () => {
    const screen = await render(
      <ConversationAvatar
        conversation={group([
          me,
          member('Camille Tremblay'),
          member('Mathis Bouchard'),
          member('Inès Côté'),
          member('Zoé'),
        ])}
        me={ME}
        size={44}
      />,
    )

    expect(letters(screen.container)).toEqual(['C', 'M', 'I'])
    const mosaic = screen.container.querySelector('[aria-hidden="true"]')
    expect(mosaic && getComputedStyle(mosaic).width).toBe('44px')
  })

  it('GRP-03, ADR 0039 (point 5) : avec moins de trois autres membres, ou seul, la mosaïque se réduit', async () => {
    const two = await render(
      <ConversationAvatar
        conversation={group([me, member('Léa'), member('Noah')])}
        me={ME}
        size={72}
      />,
    )
    expect(letters(two.container)).toEqual(['L', 'N'])
    await two.unmount()

    const alone = await render(
      <ConversationAvatar conversation={group([me], 'raquette')} me={ME} size={40} />,
    )
    expect(letters(alone.container)).toEqual(['R'])
  })
})
