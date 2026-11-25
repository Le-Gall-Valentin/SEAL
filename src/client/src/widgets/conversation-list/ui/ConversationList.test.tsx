import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import {
  type Conversation,
  ConversationsApiProvider,
  type IConversationsApi,
} from '@/entities/conversation'
import { isolate } from '@/shared/lib'
import { ConversationList } from './ConversationList'

const ME = 'me'
const member = (userId: string, username: string, displayName: string) => ({
  userId,
  username,
  displayName,
  role: 'MEMBER' as const,
})
const SKI: Conversation = {
  id: '0193a1b2-0000-7000-8000-000000000001',
  type: 'GROUP',
  name: 'Ski du dimanche',
  members: [member(ME, 'moi', 'Moi'), member('u1', 'camille.t', 'Camille Tremblay')],
  createdAt: new Date('2026-11-20T00:00:00Z'),
  lastServerSeq: 0,
}
const MATHIS: Conversation = {
  id: '0193a1b2-0000-7000-8000-000000000002',
  type: 'DIRECT',
  name: null,
  members: [member(ME, 'moi', 'Moi'), member('u2', 'mathis_b', 'Mathis Bouchard')],
  createdAt: new Date('2026-11-23T00:00:00Z'),
  lastServerSeq: 0,
}

async function show(
  list: IConversationsApi['list'],
  props: Partial<{
    variant: 'large' | 'medium' | 'small'
    selectedId: string | null
    onNew: () => void
  }> = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const api: IConversationsApi = { list, get: () => Promise.reject(new Error('non attendu')) }
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <ConversationsApiProvider api={api}>{children}</ConversationsApiProvider>
    </QueryClientProvider>
  )
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: (
          <Wrapper>
            <ConversationList
              me={ME}
              selectedId={props.selectedId ?? null}
              onNew={props.onNew ?? (() => undefined)}
              variant={props.variant ?? 'large'}
            />
          </Wrapper>
        ),
      },
    ],
    { initialEntries: ['/'] },
  )
  return render(<RouterProvider router={router} />)
}

const both: IConversationsApi['list'] = () => Promise.resolve({ items: [SKI, MATHIS], next: null })

describe('widgets/conversation-list : liste des conversations (CNV-04, CNV-10, maquette Conv-Ordinateur)', () => {
  it('CNV-04, ADR 0041 (points 1 et 2) : la plus récente d’abord, chacune un lien vers son adresse', async () => {
    const screen = await show(both, { selectedId: SKI.id })

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Conversations' }))
      .toBeVisible()
    const links = screen.getByRole('list').getByRole('link')
    await expect.element(links.nth(0)).toHaveAccessibleName('Mathis Bouchard')
    await expect.element(links.nth(1)).toHaveAccessibleName('Ski du dimanche')
    await expect.element(links.nth(1)).toHaveAttribute('href', `/?conversation=${SKI.id}`)
    await expect.element(links.nth(1)).toHaveAttribute('aria-current', 'page')
    await expect.element(links.nth(0)).not.toHaveAttribute('aria-current')
  })

  it('CNV-10 : la recherche filtre sur les noms ; sans résultat, « Effacer la recherche » rend la liste', async () => {
    const screen = await show(both)
    const search = screen.getByRole('searchbox', { name: 'Rechercher une conversation' })

    await search.fill('bouch')
    await expect
      .element(screen.getByRole('link', { name: 'Ski du dimanche' }))
      .not.toBeInTheDocument()
    await search.fill('raquette')
    await expect.element(screen.getByRole('heading', { name: 'Aucun résultat' })).toBeVisible()
    await expect
      .element(screen.getByText(`Aucune conversation ne correspond à « ${isolate('raquette')} ».`))
      .toBeVisible()
    await screen.getByRole('button', { name: 'Effacer la recherche' }).click()

    await expect.element(screen.getByRole('link', { name: 'Ski du dimanche' })).toBeVisible()
  })

  it('CNV-04, maquette Etats : sans conversation, le phoque dort et propose « Nouvelle conversation »', async () => {
    const onNew = vi.fn()
    const screen = await show(() => Promise.resolve({ items: [], next: null }), { onNew })

    await expect.element(screen.getByRole('img', { name: 'Le phoque SEAL dort' })).toBeVisible()
    await expect.element(screen.getByRole('heading', { name: 'Aucune conversation' })).toBeVisible()
    await expect
      .element(
        screen.getByText('Écrivez à un ami pour commencer. Tout sera chiffré de bout en bout.'),
      )
      .toBeVisible()
    await screen.getByRole('button', { name: 'Nouvelle conversation' }).nth(1).click()

    expect(onNew).toHaveBeenCalledTimes(1)
  })

  it('CNV-04, A10 : un échec de lecture propose de réessayer', async () => {
    const list = vi.fn<IConversationsApi['list']>(() => Promise.reject(new Error('panne')))
    const screen = await show(list)

    await expect
      .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
      .toBeVisible()
    list.mockImplementation(both)
    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByRole('link', { name: 'Ski du dimanche' })).toBeVisible()
  })

  it('CNV-04, maquettes Conv-Tablette et Conv-Telephone-Liste : bouton rond « Nouvelle conversation », recherche « Rechercher » sur tablette', async () => {
    const tablet = await show(both, { variant: 'medium' })
    await expect.element(tablet.getByRole('searchbox', { name: 'Rechercher' })).toBeVisible()
    await expect
      .element(tablet.getByRole('button', { name: 'Nouvelle conversation' }))
      .toBeVisible()
    await expect.element(tablet.getByText('Nouvelle', { exact: true })).not.toBeInTheDocument()
    await tablet.unmount()

    const desktop = await show(both)
    await expect
      .element(desktop.getByRole('button', { name: 'Nouvelle conversation' }))
      .toHaveTextContent('Nouvelle')
  })

  it('CNV-10, WCAG 2.4.3 : « Effacer la recherche » rend le focus au champ de recherche', async () => {
    const screen = await show(both)
    const search = screen.getByRole('searchbox', { name: 'Rechercher une conversation' })
    await search.fill('zz')

    await screen.getByRole('button', { name: 'Effacer la recherche' }).click()

    await expect.element(search).toHaveFocus()
    await expect.element(search).toHaveValue('')
  })

  it('CNV-04, WCAG 4.1.2 : une directe sans interlocuteur n’a pas de ligne, faute de nom', async () => {
    const alone: Conversation = { ...MATHIS, members: [member(ME, 'moi', 'Moi')] }
    const screen = await show(() => Promise.resolve({ items: [SKI, alone], next: null }))

    await expect.element(screen.getByRole('link', { name: 'Ski du dimanche' })).toBeVisible()
    expect(screen.getByRole('link').all()).toHaveLength(1)
  })
})
