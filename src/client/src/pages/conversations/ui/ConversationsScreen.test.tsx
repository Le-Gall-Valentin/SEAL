import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { page } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import {
  type Conversation,
  ConversationsApiProvider,
  type IConversationsApi,
} from '@/entities/conversation'
import { FriendsApiProvider } from '@/entities/friend'
import { AddMembersApiProvider } from '@/features/add-group-members'
import { CreateGroupApiProvider } from '@/features/create-group'
import { LeaveGroupApiProvider } from '@/features/leave-group'
import { RemoveMemberApiProvider } from '@/features/remove-group-member'
import { RenameGroupApiProvider } from '@/features/rename-group'
import { StartDirectApiProvider } from '@/features/start-direct-conversation'
import { ApiError, NetworkError } from '@/shared/lib'
import { ConversationsScreen } from './ConversationsScreen'

const ME = 'me'
const never = () => Promise.reject(new Error('non attendu'))
const member = (
  userId: string,
  username: string,
  displayName: string,
  role: 'ADMIN' | 'MEMBER' = 'MEMBER',
) => ({
  userId,
  username,
  displayName,
  role,
})
const SKI: Conversation = {
  id: '0193a1b2-0000-7000-8000-000000000001',
  type: 'GROUP',
  name: 'Ski du dimanche',
  members: [member(ME, 'moi', 'Moi', 'ADMIN'), member('u1', 'camille.t', 'Camille Tremblay')],
  createdAt: new Date('2026-11-20T00:00:00Z'),
  lastServerSeq: 0,
}

interface Fakes {
  readonly get?: IConversationsApi['get']
  readonly list?: IConversationsApi['list']
  readonly leave?: (conversationId: string) => Promise<void>
}

async function renderAt(
  entry: string,
  width: number,
  fakes: Fakes | IConversationsApi['get'] = {},
) {
  const {
    get = () => Promise.resolve(SKI),
    list = () => Promise.resolve({ items: [SKI], next: null }),
    leave = never,
  } = typeof fakes === 'function' ? { get: fakes } : fakes
  await page.viewport(width, 900)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const conversations: IConversationsApi = { list, get }
  const router = createMemoryRouter([{ path: '/', element: <ConversationsScreen me={ME} /> }], {
    initialEntries: [entry],
  })
  const screen = await render(
    <QueryClientProvider client={client}>
      <ConversationsApiProvider api={conversations}>
        <FriendsApiProvider
          api={{
            friends: () => Promise.resolve({ items: [], next: null }),
            receivedRequests: () => Promise.resolve({ items: [], next: null }),
            sentRequests: () => Promise.resolve({ items: [], next: null }),
          }}
        >
          <StartDirectApiProvider api={{ openDirect: never }}>
            <CreateGroupApiProvider api={{ createGroup: never }}>
              <AddMembersApiProvider api={{ admit: never }}>
                <RenameGroupApiProvider api={{ rename: never }}>
                  <RemoveMemberApiProvider api={{ exclude: never }}>
                    <LeaveGroupApiProvider api={{ leave }}>
                      <RouterProvider router={router} />
                    </LeaveGroupApiProvider>
                  </RemoveMemberApiProvider>
                </RenameGroupApiProvider>
              </AddMembersApiProvider>
            </CreateGroupApiProvider>
          </StartDirectApiProvider>
        </FriendsApiProvider>
      </ConversationsApiProvider>
    </QueryClientProvider>,
  )
  return Object.assign(screen, { router })
}

describe('pages/conversations : écran Conversations (CNV-04, ADR 0041)', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('Maquette Etats : sur ordinateur, la liste et « Vos conversations sont scellées » sans conversation choisie', async () => {
    const screen = await renderAt('/', 1440)

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toBeVisible()
    await expect.element(screen.getByRole('link', { name: 'Ski du dimanche' })).toBeVisible()
    await expect
      .element(screen.getByRole('heading', { name: 'Vos conversations sont scellées' }))
      .toBeVisible()
  })

  it('CNV-04, ADR 0041 (point 1) : /?conversation={id} ouvre la conversation, avec ses infos à droite sur ordinateur', async () => {
    const screen = await renderAt(`/?conversation=${SKI.id}`, 1440)

    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Ski du dimanche' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('complementary', { name: 'Infos du groupe' }))
      .toBeVisible()
    await screen.getByRole('button', { name: 'Infos du groupe' }).click()
    await expect
      .element(screen.getByRole('complementary', { name: 'Infos du groupe' }))
      .not.toBeInTheDocument()
  })

  it('Maquettes Conv-Telephone-Liste et Conv-Telephone-Fil : sur téléphone, la liste ou le fil, et « Retour »', async () => {
    const screen = await renderAt(`/?conversation=${SKI.id}`, 390)

    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Ski du dimanche' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .not.toBeInTheDocument()
    await screen.getByRole('button', { name: 'Retour' }).click()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Ski du dimanche' }))
      .not.toBeInTheDocument()
  })

  it('CNV-06, ADR 0041 (point 4) : une conversation disparue (404) ramène à la liste avec l’alerte', async () => {
    const screen = await renderAt(`/?conversation=${SKI.id}`, 390, () =>
      Promise.reject(new ApiError(404, 'NOT_FOUND')),
    )

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Cette conversation n’est plus disponible.')
    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toBeVisible()
  })

  it('CNV-01 : « Nouvelle conversation » ouvre le dialogue', async () => {
    const screen = await renderAt('/', 1440)

    await screen.getByRole('button', { name: 'Nouvelle conversation' }).first().click()

    await expect
      .element(screen.getByRole('dialog', { name: 'Nouvelle conversation' }))
      .toBeVisible()
  })

  it('CNV-06, ADR 0041 (point 4) : une conversation disparue ramène l’adresse à la liste, avec l’alerte', async () => {
    const screen = await renderAt(`/?conversation=${SKI.id}`, 1440, () =>
      Promise.reject(new ApiError(404, 'NOT_FOUND')),
    )

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Cette conversation n’est plus disponible.')
    await expect.poll(() => screen.router.state.location.search).toBe('')
  })

  it('ADR 0041 (point 4) : une directe dont l’autre compte n’est plus actif ramène aussi à la liste', async () => {
    const alone: Conversation = {
      ...SKI,
      type: 'DIRECT',
      name: null,
      members: [member(ME, 'moi', 'Moi')],
    }
    const screen = await renderAt(`/?conversation=${SKI.id}`, 1440, () => Promise.resolve(alone))

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Cette conversation n’est plus disponible.')
    await expect.poll(() => screen.router.state.location.search).toBe('')
  })

  it('A10 : sur téléphone, un autre échec propose « Réessayer » et « Retour »', async () => {
    const screen = await renderAt(`/?conversation=${SKI.id}`, 390, () =>
      Promise.reject(new NetworkError()),
    )

    await expect.element(screen.getByRole('button', { name: 'Réessayer' })).toBeVisible()
    await screen.getByRole('button', { name: 'Retour' }).click()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toBeVisible()
  })

  it('GRP-03, WCAG 2.4.3 : quitter un groupe ramène à la liste sans alerte, le focus sur son titre', async () => {
    let left = false
    const screen = await renderAt(`/?conversation=${SKI.id}`, 1440, {
      get: () => (left ? Promise.reject(new ApiError(404, 'NOT_FOUND')) : Promise.resolve(SKI)),
      list: () => Promise.resolve({ items: left ? [] : [SKI], next: null }),
      leave: () => {
        left = true
        return Promise.resolve()
      },
    })

    await screen
      .getByRole('complementary')
      .getByRole('button', { name: 'Quitter le groupe' })
      .click()
    await screen.getByRole('alertdialog').getByRole('button', { name: 'Quitter le groupe' }).click()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toHaveFocus()
    expect(screen.router.state.location.search).toBe('')
    expect(screen.getByText('Cette conversation n’est plus disponible.').elements()).toHaveLength(0)

    // Réinvité, l'utilisateur rouvre le même groupe.
    left = false
    await screen.router.navigate(`/?conversation=${SKI.id}`)
    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Ski du dimanche' }))
      .toBeVisible()
  })
})
