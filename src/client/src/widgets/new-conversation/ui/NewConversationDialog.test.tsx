import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { page, userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import type { Conversation } from '@/entities/conversation'
import { type Friend, FriendsApiProvider, type IFriendsApi } from '@/entities/friend'
import { CreateGroupApiProvider, type ICreateGroupApi } from '@/features/create-group'
import { type IStartDirectApi, StartDirectApiProvider } from '@/features/start-direct-conversation'
import { ApiError } from '@/shared/lib'
import { NewConversationDialog } from './NewConversationDialog'

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
const CAMILLE = friend('camille.t', 'Camille Tremblay')
const INES = friend('ines.c', 'Inès Côté')
const VIEW: Conversation = {
  id: 'c-1',
  type: 'GROUP',
  name: 'Ski',
  members: [],
  createdAt: new Date(0),
  lastServerSeq: 0,
}

function setup(apis: { direct?: Partial<IStartDirectApi>; group?: Partial<ICreateGroupApi> } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const friends: IFriendsApi = {
    friends: () => Promise.resolve({ items: [CAMILLE, INES], next: null }),
    receivedRequests: () => Promise.resolve({ items: [], next: null }),
    sentRequests: () => Promise.resolve({ items: [], next: null }),
  }
  const direct: IStartDirectApi = {
    openDirect: () =>
      Promise.resolve({ created: true, conversation: { ...VIEW, type: 'DIRECT', name: null } }),
    ...apis.direct,
  }
  const group: ICreateGroupApi = { createGroup: () => Promise.resolve(VIEW), ...apis.group }
  const onOpened = vi.fn()
  const onClose = vi.fn()
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <FriendsApiProvider api={friends}>
        <StartDirectApiProvider api={direct}>
          <CreateGroupApiProvider api={group}>{children}</CreateGroupApiProvider>
        </StartDirectApiProvider>
      </FriendsApiProvider>
    </QueryClientProvider>
  )
  return { Wrapper, onOpened, onClose }
}

async function open(apis?: Parameters<typeof setup>[0]) {
  const { Wrapper, onOpened, onClose } = setup(apis)
  const screen = await render(
    <Wrapper>
      <NewConversationDialog open onClose={onClose} onOpened={onOpened} />
    </Wrapper>,
  )
  return { screen, onOpened, onClose }
}

describe('widgets/new-conversation : Nouvelle conversation (CNV-01, CNV-03, ADR 0041)', () => {
  it('CNV-01, ADR 0041 (point 3) : « Message direct » d’abord ; un ami choisi, « Ouvrir la conversation » l’ouvre', async () => {
    const openDirect = vi.fn(() =>
      Promise.resolve({
        created: false,
        conversation: { ...VIEW, id: 'd-1', type: 'DIRECT' as const, name: null },
      }),
    )
    const { screen, onOpened } = await open({ direct: { openDirect } })

    await expect
      .element(screen.getByRole('dialog', { name: 'Nouvelle conversation' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('radio', { name: 'Message direct' }))
      .toHaveAttribute('aria-checked', 'true')
    const submit = screen.getByRole('button', { name: 'Ouvrir la conversation' })
    await expect.element(submit).toBeDisabled()
    await screen.getByRole('option', { name: /Inès Côté/u }).click()
    await expect
      .element(screen.getByRole('combobox', { name: 'Message direct' }))
      .toHaveAttribute('aria-disabled', 'true')
    await submit.click()

    await expect.poll(() => onOpened.mock.calls).toEqual([['d-1']])
    expect(openDirect).toHaveBeenCalledWith(INES.userId)
  })

  it('CNV-03, GRP-01 : en « Groupe », « Créer le groupe (n) » compte le créateur et attend un nom et un membre', async () => {
    const createGroup = vi.fn(() => Promise.resolve(VIEW))
    const { screen, onOpened } = await open({ group: { createGroup } })

    await screen.getByRole('radio', { name: 'Message direct' }).click()
    await userEvent.keyboard('{ArrowRight}')
    await expect
      .element(screen.getByRole('radio', { name: 'Groupe' }))
      .toHaveAttribute('aria-checked', 'true')
    await expect.element(screen.getByRole('button', { name: 'Créer le groupe (1)' })).toBeDisabled()
    await screen.getByRole('textbox', { name: 'Nom du groupe' }).fill('Révisions intra')
    await screen.getByRole('option', { name: /Camille Tremblay/u }).click()
    await screen.getByRole('option', { name: /Inès Côté/u }).click()
    await screen.getByRole('button', { name: 'Créer le groupe (3)' }).click()

    await expect.poll(() => onOpened.mock.calls).toEqual([['c-1']])
    expect(createGroup).toHaveBeenCalledWith('Révisions intra', [CAMILLE.userId, INES.userId])
  })

  it('GRP-01, ADR 0040 : un nom invisible est signalé une fois le champ quitté, et bloque la création', async () => {
    const { screen } = await open()

    await screen.getByRole('radio', { name: 'Groupe' }).click()
    await screen
      .getByRole('textbox', { name: 'Nom du groupe' })
      .fill(`S${String.fromCodePoint(0x200b)}ki`)
    await userEvent.keyboard('{Tab}')
    await screen.getByRole('option', { name: /Camille Tremblay/u }).click()

    await expect
      .element(screen.getByText('1 à 64 caractères, dont un visible au moins.'))
      .toBeVisible()
    await expect.element(screen.getByRole('button', { name: 'Créer le groupe (2)' })).toBeDisabled()
  })

  it('FRD-21, ADR 0041 (point 4) : un ami retiré entre-temps, le refus s’annonce', async () => {
    const { screen, onOpened } = await open({
      direct: { openDirect: () => Promise.reject(new ApiError(403, 'NOT_FRIENDS')) },
    })

    await screen.getByRole('option', { name: /Camille Tremblay/u }).click()
    await screen.getByRole('button', { name: 'Ouvrir la conversation' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent(
        'Vous ne pouvez ajouter que vos amis. Leur liste vient d’être mise à jour.',
      )
    expect(onOpened).not.toHaveBeenCalled()
  })

  it('ADR 0041 (point 3) : « Annuler » ferme la fenêtre', async () => {
    const { screen, onClose } = await open()

    await screen.getByRole('button', { name: 'Annuler' }).click()

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('ADR 0041 (point 3) : sur téléphone, Nouvelle conversation devient une feuille plein écran', async () => {
    await page.viewport(390, 844)
    try {
      const { screen } = await open()
      const sheet = screen
        .getByRole('dialog', { name: 'Nouvelle conversation' })
        .element()
        .getBoundingClientRect()

      expect(Math.round(sheet.width)).toBe(390)
      expect(Math.round(sheet.height)).toBe(844)
    } finally {
      await page.viewport(1280, 800)
    }
  })
})
