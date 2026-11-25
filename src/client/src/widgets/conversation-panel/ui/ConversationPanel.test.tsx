import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Conversation } from '@/entities/conversation'
import { ConversationPanel } from './ConversationPanel'

const ME = 'me'
const member = (userId: string, username: string, displayName: string) => ({
  userId,
  username,
  displayName,
  role: 'MEMBER' as const,
})
const GROUP: Conversation = {
  id: 'g-1',
  type: 'GROUP',
  name: 'Équipe 8INF138',
  members: [
    member('u1', 'camille.t', 'Camille Tremblay'),
    member(ME, 'moi', 'Moi Même'),
    member('u2', 'mathis_b', 'Mathis Bouchard'),
    member('u3', 'ines.c', 'Inès Côté'),
  ],
  createdAt: new Date(0),
  lastServerSeq: 0,
}
const DIRECT: Conversation = {
  ...GROUP,
  id: 'd-1',
  type: 'DIRECT',
  name: null,
  members: [member(ME, 'moi', 'Moi Même'), member('u2', 'mathis_b', 'Mathis Bouchard')],
}

const noop = () => undefined

describe('widgets/conversation-panel : en-tête et fil (CNV-04, GRP-05, ADR 0041 point 6)', () => {
  it('CNV-04, ADR 0017 : des prénoms écrits de droite à gauche restent isolés dans « … et vous »', async () => {
    const rtl: Conversation = {
      ...GROUP,
      members: [
        member(ME, 'moi', 'Moi Même'),
        member('u1', 'a.b', '\u05D0\u05D1\u05D2 X'),
        member('u2', 'c.d', '\u05D3\u05D4\u05D5'),
      ],
    }
    const screen = await render(
      <ConversationPanel
        me={ME}
        conversation={rtl}
        variant="large"
        infoOpen={false}
        onToggleInfo={noop}
        onBack={noop}
        onNew={noop}
      />,
    )

    const subtitle = screen.getByText(/et vous$/u).element()
    expect(subtitle.textContent).toBe(
      '\u2068\u05D0\u05D1\u05D2\u2069, \u2068\u05D3\u05D4\u05D5\u2069 et vous',
    )
  })

  it('CNV-04, maquette Conv-Ordinateur : un groupe montre ses membres « … et vous » et le bouton d’infos', async () => {
    const onToggleInfo = vi.fn()
    const screen = await render(
      <ConversationPanel
        me={ME}
        conversation={GROUP}
        variant="large"
        infoOpen
        onToggleInfo={onToggleInfo}
        onBack={noop}
        onNew={noop}
      />,
    )

    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Équipe 8INF138' }))
      .toBeVisible()
    await expect
      .element(
        screen.getByText(/^\u2068Camille\u2069, \u2068Mathis\u2069, \u2068Inès\u2069 et vous$/u),
      )
      .toBeVisible()
    const info = screen.getByRole('button', { name: 'Infos du groupe' })
    await expect.element(info).toHaveAttribute('aria-expanded', 'true')
    await info.click()
    expect(onToggleInfo).toHaveBeenCalledTimes(1)
    await expect.element(screen.getByRole('button', { name: 'Retour' })).not.toBeInTheDocument()
  })

  it('GRP-05, ADR 0041 (points 4 et 6) : fil vide, annonce de l’arrivée dans un groupe, aucun composeur', async () => {
    const screen = await render(
      <ConversationPanel
        me={ME}
        conversation={GROUP}
        variant="large"
        infoOpen={false}
        onToggleInfo={noop}
        onBack={noop}
        onNew={noop}
      />,
    )

    await expect.element(screen.getByText('Aucun message')).toBeVisible()
    await expect
      .element(screen.getByText('Les messages de cette conversation apparaîtront ici.'))
      .toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Vous ne voyez que les messages envoyés depuis votre arrivée dans le groupe.',
        ),
      )
      .toBeVisible()
    await expect.element(screen.getByRole('textbox')).not.toBeInTheDocument()
  })

  it('CNV-04, maquette Conv-Telephone-Fil : une directe montre l’identifiant, « Retour » sur téléphone, sans annonce ni infos', async () => {
    const onBack = vi.fn()
    const screen = await render(
      <ConversationPanel
        me={ME}
        conversation={DIRECT}
        variant="small"
        infoOpen={false}
        onToggleInfo={noop}
        onBack={onBack}
        onNew={noop}
      />,
    )

    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Mathis Bouchard' }))
      .toBeVisible()
    await expect.element(screen.getByText('@mathis_b')).toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Infos du groupe' }))
      .not.toBeInTheDocument()
    await expect
      .element(
        screen.getByText(
          'Vous ne voyez que les messages envoyés depuis votre arrivée dans le groupe.',
        ),
      )
      .not.toBeInTheDocument()
    await screen.getByRole('button', { name: 'Retour' }).click()
    expect(onBack).toHaveBeenCalledTimes(1)
  })

  it('CNV-04, maquette Etats : sans conversation choisie, « Vos conversations sont scellées » propose d’en commencer une', async () => {
    const onNew = vi.fn()
    const screen = await render(
      <ConversationPanel
        me={ME}
        conversation={null}
        variant="large"
        infoOpen={false}
        onToggleInfo={noop}
        onBack={noop}
        onNew={onNew}
      />,
    )

    await expect
      .element(screen.getByRole('heading', { name: 'Vos conversations sont scellées' }))
      .toBeVisible()
    await expect
      .element(
        screen.getByText('Choisissez une conversation, ou commencez-en une nouvelle avec un ami.'),
      )
      .toBeVisible()
    await screen.getByRole('button', { name: 'Nouvelle conversation' }).click()
    expect(onNew).toHaveBeenCalledTimes(1)
  })
})
