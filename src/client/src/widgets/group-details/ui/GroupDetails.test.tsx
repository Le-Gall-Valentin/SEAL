import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { page, userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import type { Conversation, Member } from '@/entities/conversation'
import { type Friend, FriendsApiProvider } from '@/entities/friend'
import { AddMembersApiProvider } from '@/features/add-group-members'
import { LeaveGroupApiProvider } from '@/features/leave-group'
import { RemoveMemberApiProvider } from '@/features/remove-group-member'
import { RenameGroupApiProvider } from '@/features/rename-group'
import { isolate } from '@/shared/lib'
import { GroupDetails } from './GroupDetails'

const ME = 'me'
const member = (
  userId: string,
  username: string,
  displayName: string,
  role: Member['role'] = 'MEMBER',
): Member => ({
  userId,
  username,
  displayName,
  role,
})
const CAMILLE = member('u1', 'camille.t', 'Camille Tremblay')
const MATHIS = member('u2', 'mathis_b', 'Mathis Bouchard')
const asFriend = (m: Member): Friend => ({
  userId: m.userId,
  username: m.username,
  displayName: m.displayName,
  friendsSince: new Date(0),
  lastInteractionAt: null,
  interactionCount30d: 0,
})

function group(role: Member['role']): Conversation {
  return {
    id: 'g-1',
    type: 'GROUP',
    name: 'Équipe 8INF138',
    members: [
      member(ME, 'moi', 'Moi Même', role),
      CAMILLE,
      MATHIS,
      member('u3', 'ines.c', 'Inès Côté', role === 'ADMIN' ? 'MEMBER' : 'ADMIN'),
    ],
    createdAt: new Date(2026, 9, 28, 12),
    lastServerSeq: 0,
  }
}

async function show(
  conversation: Conversation,
  variant: 'aside' | 'drawer',
  apis: {
    leave?: () => Promise<void>
    exclude?: () => Promise<void>
    rename?: () => Promise<Conversation>
  } = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onLeft = vi.fn()
  const onClose = vi.fn()
  const exclude = vi.fn(apis.exclude ?? (() => Promise.resolve()))
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <FriendsApiProvider
        api={{
          friends: () => Promise.resolve({ items: [asFriend(CAMILLE)], next: null }),
          receivedRequests: () => Promise.resolve({ items: [], next: null }),
          sentRequests: () => Promise.resolve({ items: [], next: null }),
        }}
      >
        <AddMembersApiProvider api={{ admit: () => Promise.resolve(conversation) }}>
          <RenameGroupApiProvider
            api={{ rename: apis.rename ?? (() => Promise.resolve(conversation)) }}
          >
            <RemoveMemberApiProvider api={{ exclude }}>
              <LeaveGroupApiProvider api={{ leave: apis.leave ?? (() => Promise.resolve()) }}>
                {children}
              </LeaveGroupApiProvider>
            </RemoveMemberApiProvider>
          </RenameGroupApiProvider>
        </AddMembersApiProvider>
      </FriendsApiProvider>
    </QueryClientProvider>
  )
  const screen = await render(
    <Wrapper>
      <GroupDetails
        me={ME}
        conversation={conversation}
        variant={variant}
        open
        onClose={onClose}
        onLeft={onLeft}
      />
    </Wrapper>,
  )
  return { screen, onLeft, onClose, exclude }
}

describe('widgets/group-details : infos du groupe (GRP-03, GRP-04, ADR 0041)', () => {
  it('GRP-03, ADR 0041 (point 5) : l’administrateur renomme et retire les autres ; méta sans créateur', async () => {
    const { screen } = await show(group('ADMIN'), 'aside')

    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Infos du groupe' }))
      .toBeVisible()
    await expect.element(screen.getByText('4 membres · Créé le 28 oct.')).toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Renommer le groupe' })).toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Les messages sont chiffrés pour chaque membre et signés par leur auteur.',
        ),
      )
      .toBeVisible()
    await expect
      .element(
        screen.getByRole('button', { name: `Retirer ${isolate('Camille Tremblay')} du groupe` }),
      )
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: `Retirer ${isolate('Moi Même')} du groupe` }))
      .not.toBeInTheDocument()
    await expect.element(screen.getByRole('button', { name: 'Fermer le panneau' })).toBeVisible()
  })

  it('GRP-04 : « Vous », « Admin » et « Pas dans vos amis » pour un membre qui n’est pas un ami', async () => {
    const { screen } = await show(group('ADMIN'), 'aside')
    await expect.element(screen.getByText('Pas dans vos amis').first()).toBeVisible()
    const rows = screen
      .getByRole('listitem')
      .all()
      .map((row) => row.element().textContent)

    expect(rows[0]).toMatch(/Vous.*Admin/u)
    expect(rows[1]).not.toMatch(/Pas dans vos amis/u)
    expect(rows[2]).toMatch(/Mathis Bouchard.*Pas dans vos amis.*@mathis_b/u)
  })

  it('GRP-03, maquette Conv-Tablette : un membre ajoute et part, sans renommer ni retirer ; tiroir à « Fermer »', async () => {
    const { screen } = await show(group('MEMBER'), 'drawer')

    await expect.element(screen.getByRole('dialog', { name: 'Infos du groupe' })).toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Renommer le groupe' }))
      .not.toBeInTheDocument()
    await expect.element(screen.getByRole('button', { name: /^Retirer/u })).not.toBeInTheDocument()
    await expect.element(screen.getByRole('button', { name: 'Ajouter des membres' })).toBeVisible()
    await expect
      .element(
        screen.getByText(
          'Vous pouvez ajouter uniquement vos amis. Les membres ne sont pas forcément amis entre eux.',
        ),
      )
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Fermer', exact: true })).toBeVisible()
  })

  it('GRP-03 : « Quitter le groupe », confirmé, ramène à la liste', async () => {
    const leave = vi.fn(() => Promise.resolve())
    const { screen, onLeft } = await show(group('MEMBER'), 'aside', { leave })

    await screen.getByRole('button', { name: 'Quitter le groupe' }).click()
    await screen.getByRole('alertdialog').getByRole('button', { name: 'Quitter le groupe' }).click()

    await expect.poll(() => onLeft.mock.calls.length).toBe(1)
  })

  it('GRP-03 : retirer un membre passe par la confirmation', async () => {
    const { screen, exclude } = await show(group('ADMIN'), 'aside')

    await screen
      .getByRole('button', { name: `Retirer ${isolate('Camille Tremblay')} du groupe` })
      .click()
    await screen
      .getByRole('alertdialog')
      .getByRole('button', { name: 'Retirer', exact: true })
      .click()

    await expect.poll(() => exclude.mock.calls).toEqual([['g-1', 'u1']])
  })

  it('ADR 0041 (point 4) : un groupe d’un seul membre se dit au singulier', async () => {
    const alone: Conversation = {
      ...group('ADMIN'),
      members: [member(ME, 'moi', 'Moi Même', 'ADMIN')],
    }
    const { screen } = await show(alone, 'aside')

    await expect.element(screen.getByText('1 membre · Créé le 28 oct.')).toBeVisible()
  })

  it('ADR 0035, ADR 0041 (point 3) : Échap dans un dialogue ouvert depuis le tiroir ne ferme que ce dialogue', async () => {
    const { screen, onClose } = await show(group('ADMIN'), 'drawer')

    await screen.getByRole('button', { name: 'Renommer le groupe' }).click()
    await expect.element(screen.getByRole('dialog', { name: 'Renommer le groupe' })).toBeVisible()
    await userEvent.keyboard('{Escape}')

    await expect
      .element(screen.getByRole('dialog', { name: 'Renommer le groupe' }))
      .not.toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('ADR 0035 : pendant un envoi, Échap ne ferme ni le dialogue ni le tiroir', async () => {
    const { screen, onClose } = await show(group('ADMIN'), 'drawer', {
      rename: () => new Promise(() => undefined),
    })

    await screen.getByRole('button', { name: 'Renommer le groupe' }).click()
    await screen.getByRole('textbox', { name: 'Nom du groupe' }).fill('Autre nom')
    await screen.getByRole('button', { name: 'Enregistrer' }).click()
    await userEvent.keyboard('{Escape}')

    await expect.element(screen.getByRole('dialog', { name: 'Renommer le groupe' })).toBeVisible()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('WCAG 2.4.3 : un dialogue annulé rend le focus à son déclencheur ; après un retrait, au titre des membres', async () => {
    const { screen } = await show(group('ADMIN'), 'aside')

    await screen.getByRole('button', { name: 'Renommer le groupe' }).click()
    await screen.getByRole('button', { name: 'Annuler' }).click()
    await expect.element(screen.getByRole('button', { name: 'Renommer le groupe' })).toHaveFocus()

    await screen
      .getByRole('button', { name: `Retirer ${isolate('Camille Tremblay')} du groupe` })
      .click()
    await screen
      .getByRole('alertdialog')
      .getByRole('button', { name: 'Retirer', exact: true })
      .click()
    await expect.element(screen.getByRole('heading', { level: 3, name: '4 membres' })).toHaveFocus()
  })

  it('ADR 0041 (point 3) : sur téléphone, le tiroir devient une feuille plein écran', async () => {
    await page.viewport(390, 844)
    try {
      const { screen } = await show(group('MEMBER'), 'drawer')
      const sheet = screen
        .getByRole('dialog', { name: 'Infos du groupe' })
        .element()
        .getBoundingClientRect()

      expect(Math.round(sheet.width)).toBe(390)
      expect(Math.round(sheet.height)).toBe(844)
    } finally {
      await page.viewport(1280, 800)
    }
  })
})
