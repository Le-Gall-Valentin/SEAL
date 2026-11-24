import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, userEvent } from 'vitest/browser'
import { render } from 'vitest-browser-react'
import {
  type Friend,
  FriendsApiProvider,
  type IFriendsApi,
  type ReceivedRequest,
  type SentRequest,
} from '@/entities/friend'
import { AnswerFriendRequestApiProvider } from '@/features/answer-friend-request'
import { RemoveFriendApiProvider } from '@/features/remove-friend'
import { SendFriendRequestApiProvider } from '@/features/send-friend-request'
import { ApiError, isolate, NetworkError } from '@/shared/lib'
import { FriendsPage } from './FriendsPage'

const id = (n: number) => `0192f7a1-0000-7000-8000-${String(n).padStart(12, '0')}`
const friend = (n: number, displayName: string, username: string): Friend => ({
  userId: id(n),
  username,
  displayName,
  friendsSince: new Date(0),
})
const FRIENDS = [
  friend(1, 'Thomas Lavoie', 'thomas.lavoie'),
  friend(2, 'Inès Côté', 'ines.cote'),
  friend(3, 'Camille Tremblay', 'camille.tremblay'),
]
const RECEIVED: ReceivedRequest[] = [
  {
    id: id(11),
    userId: id(21),
    username: 'sarah.belanger',
    displayName: 'Sarah Bélanger',
    createdAt: new Date(),
  },
  {
    id: id(12),
    userId: id(22),
    username: 'jade.morin',
    displayName: 'Jade Morin',
    createdAt: new Date(),
  },
]
const SENT: SentRequest[] = [
  { id: id(13), userId: id(23), username: 'antoine.roy', createdAt: new Date() },
]

const never = () => Promise.reject(new Error('non attendu'))

interface Fakes {
  readonly friends?: IFriendsApi['friends']
  readonly received?: readonly ReceivedRequest[]
  readonly receivedApi?: IFriendsApi['receivedRequests']
  readonly sentApi?: IFriendsApi['sentRequests']
  readonly send?: (username: string) => Promise<SentRequest>
  readonly accept?: (requestId: string) => Promise<void>
  readonly unfriend?: (userId: string) => Promise<void>
  readonly client?: QueryClient
}

async function renderAt(entry: string, width: number, height: number, fakes: Fakes = {}) {
  await page.viewport(width, height)
  const router = createMemoryRouter([{ path: '/amis', element: <FriendsPage /> }], {
    initialEntries: [entry],
  })
  const client = fakes.client ?? new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const friendsApi: IFriendsApi = {
    friends: fakes.friends ?? (() => Promise.resolve({ items: FRIENDS, next: null })),
    receivedRequests:
      fakes.receivedApi ??
      (() => Promise.resolve({ items: fakes.received ?? RECEIVED, next: null })),
    sentRequests: fakes.sentApi ?? (() => Promise.resolve({ items: SENT, next: null })),
  }
  const accept = vi.fn(fakes.accept ?? (() => Promise.resolve()))
  const screen = await render(
    <QueryClientProvider client={client}>
      <FriendsApiProvider api={friendsApi}>
        <SendFriendRequestApiProvider api={{ send: fakes.send ?? never }}>
          <AnswerFriendRequestApiProvider api={{ accept, decline: never, revoke: never }}>
            <RemoveFriendApiProvider api={{ unfriend: fakes.unfriend ?? never }}>
              <RouterProvider router={router} />
            </RemoveFriendApiProvider>
          </AnswerFriendRequestApiProvider>
        </SendFriendRequestApiProvider>
      </FriendsApiProvider>
    </QueryClientProvider>,
  )
  return { screen, accept, client }
}

describe('pages/friends : écran Amis (Amis-Ordinateur, Amis-Tablette, Amis-Telephone, ADR 0038)', () => {
  afterEach(async () => {
    await page.viewport(1280, 800)
  })

  it('FRD-10, Amis-Ordinateur : titre, onglets, amis triés par nom, compteur, aside « Ajouter un ami »', async () => {
    const { screen } = await renderAt('/amis', 1440, 900)

    await expect.element(screen.getByRole('heading', { level: 1, name: 'Amis' })).toBeVisible()
    const tabs = screen.getByRole('navigation', { name: 'Sections' })
    await expect
      .element(tabs.getByRole('link', { name: /^Amis/ }))
      .toHaveAttribute('aria-current', 'page')
    await expect.element(tabs.getByLabelText('2 invitations en attente')).toBeVisible()
    await expect.element(screen.getByText('3 amis')).toBeVisible()
    const names = screen
      .getByRole('listitem')
      .elements()
      .map((item) => item.querySelector('span.truncate.font-semibold')?.textContent)
    expect(names).toEqual(['Camille Tremblay', 'Inès Côté', 'Thomas Lavoie'])
    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Ajouter un ami' }))
      .toBeVisible()
    await expect.element(screen.getByLabelText('Identifiant')).toBeVisible()
    expect(screen.getByRole('button', { name: /Envoyer un message/ }).elements()).toHaveLength(0)
  })

  it('FRD-11, ADR 0038 (point 3) : le menu d’un ami n’offre que « Retirer des amis », qui demande confirmation', async () => {
    const { screen } = await renderAt('/amis', 1440, 900)

    const more = screen.getByRole('button', { name: 'Plus d’actions — Inès Côté' })
    await more.click()

    const menu = screen.getByRole('menu', { name: 'Actions — Inès Côté' })
    await expect.element(menu).toBeVisible()
    expect(menu.getByRole('menuitem').elements()).toHaveLength(1)
    await expect.element(menu.getByRole('menuitem', { name: 'Retirer des amis' })).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    await expect.element(more).toHaveFocus()
    expect(screen.getByRole('menu').elements()).toHaveLength(0)

    await more.click()
    await screen.getByRole('menuitem', { name: 'Retirer des amis' }).click()
    await expect
      .element(
        screen.getByRole('alertdialog', { name: `Retirer ${isolate('Inès Côté')} de vos amis ?` }),
      )
      .toBeVisible()
  })

  it('ADR 0038 (point 6) : la recherche filtre sans diacritiques ; sans résultat, « Effacer la recherche »', async () => {
    const { screen } = await renderAt('/amis', 1440, 900)

    await screen.getByRole('searchbox', { name: 'Rechercher un ami' }).fill('ines')
    await expect.poll(() => screen.getByRole('listitem').elements().length).toBe(1)
    await screen.getByRole('searchbox', { name: 'Rechercher un ami' }).fill('zzz')
    await expect.element(screen.getByText('Aucun ami ne correspond à « zzz ».')).toBeVisible()
    // ADR 0038 (point 9) : la carte « Conversations · recherche » d'Etats.
    await expect
      .element(screen.getByRole('heading', { level: 2, name: 'Aucun résultat' }))
      .toBeVisible()
    await expect.element(screen.getByRole('img', { name: 'Le phoque SEAL cherche' })).toBeVisible()
    await screen.getByRole('button', { name: 'Effacer la recherche' }).click()
    await expect.poll(() => screen.getByRole('listitem').elements().length).toBe(3)
  })

  it('FRD-01, Amis-Tablette : onglet Invitations, reçues puis envoyées ; ajout par le bouton et la feuille', async () => {
    const { screen } = await renderAt('/amis?onglet=invitations', 820, 1180)

    await expect
      .element(screen.getByRole('link', { name: /^Invitations/ }))
      .toHaveAttribute('aria-current', 'page')
    await expect.element(screen.getByRole('region', { name: /Reçues/ })).toBeVisible()
    await expect.element(screen.getByText('Sarah Bélanger')).toBeVisible()
    await expect.element(screen.getByRole('region', { name: 'Envoyées' })).toBeVisible()
    await expect.element(screen.getByText('@antoine.roy')).toBeVisible()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Ajouter un ami' }).elements(),
    ).toHaveLength(0)

    await screen.getByRole('button', { name: 'Ajouter un ami' }).click()
    await expect.element(screen.getByRole('dialog', { name: 'Ajouter un ami' })).toBeVisible()
    await screen.getByRole('button', { name: 'Fermer' }).click()
    expect(screen.getByRole('dialog').elements()).toHaveLength(0)
  })

  it('ADR 0038 (point 9), Etats « Amis · compte neuf » : mesures de la carte', async () => {
    const { screen } = await renderAt('/amis', 1440, 900, {
      friends: () => Promise.resolve({ items: [], next: null }),
    })
    const title = screen.getByRole('heading', { name: 'Pas encore d’amis' })
    await expect.element(title).toBeVisible()

    const card = title.element().parentElement
    const text = title.element().nextElementSibling
    const button = text?.nextElementSibling
    expect(card && getComputedStyle(card).padding).toBe('22px 22px 24px')
    expect(getComputedStyle(title.element()).marginTop).toBe('12px')
    expect(getComputedStyle(title.element()).letterSpacing).toBe('-0.18px')
    expect(text && getComputedStyle(text).marginTop).toBe('6px')
    expect(text && getComputedStyle(text).maxWidth).toBe('260px')
    expect(button && getComputedStyle(button).marginTop).toBe('16px')
    expect(button && getComputedStyle(button).paddingLeft).toBe('12px')
  })

  it('ADR 0038 (point 9), Amis-Tablette : 10 px entre le titre d’une section d’invitations et sa liste', async () => {
    const { screen } = await renderAt('/amis?onglet=invitations', 820, 1180)

    const title = screen.getByRole('heading', { level: 2, name: /Reçues/ })
    await expect.element(title).toBeVisible()

    expect(getComputedStyle(title.element()).marginBottom).toBe('10px')
  })

  it('ADR 0038 (point 2) : invitations vides annoncées', async () => {
    const { screen } = await renderAt('/amis?onglet=invitations', 820, 1180, { received: [] })

    await expect.element(screen.getByText('Aucune invitation reçue.')).toBeVisible()
  })

  it('FRD-01, ADR 0038 (point 8) : « Accepter l’invitation » du formulaire accepte l’invitation du 409', async () => {
    const { screen, accept } = await renderAt('/amis', 1440, 900, {
      send: () =>
        Promise.reject(new ApiError(409, 'INCOMING_REQUEST_EXISTS', { requestId: id(11) })),
    })

    await screen.getByLabelText('Identifiant').fill('sarah.belanger')
    await screen.getByRole('button', { name: 'Envoyer l’invitation' }).click()
    await screen.getByRole('button', { name: 'Accepter l’invitation' }).click()

    await expect.poll(() => accept.mock.calls).toEqual([[id(11)]])
  })

  describe('FRD-01 : acceptation depuis le formulaire', () => {
    const incoming = () =>
      Promise.reject(new ApiError(409, 'INCOMING_REQUEST_EXISTS', { requestId: id(11) }))

    async function sendAndAccept(screen: Awaited<ReturnType<typeof renderAt>>['screen']) {
      await screen.getByLabelText('Identifiant').fill('sarah.belanger')
      await screen.getByRole('button', { name: 'Envoyer l’invitation' }).click()
      await screen.getByRole('button', { name: 'Accepter l’invitation' }).click()
    }

    async function announcedOnce(
      screen: Awaited<ReturnType<typeof renderAt>>['screen'],
      text: string,
    ) {
      await expect.poll(() => screen.getByText(text).elements().length).toBeGreaterThan(0)
      await new Promise((resolve) => setTimeout(resolve, 100))
      expect(screen.getByText(text).elements()).toHaveLength(1)
    }

    it('FRD-01, ADR 0038 (point 9) : acceptée, le champ se vide', async () => {
      const { screen } = await renderAt('/amis', 1440, 900, { send: incoming })

      await sendAndAccept(screen)

      await expect.element(screen.getByLabelText('Identifiant')).toHaveValue('')
    })

    it('FRD-01, ADR 0038 (point 9) : une invitation close entre-temps n’est annoncée qu’une fois', async () => {
      const { screen } = await renderAt('/amis', 1440, 900, {
        send: incoming,
        accept: () => Promise.reject(new ApiError(404, 'NOT_FOUND')),
      })

      await sendAndAccept(screen)

      await announcedOnce(screen, 'Cette invitation n’existe plus.')
    })

    it('FRD-01, ADR 0038 (point 9) : un échec réseau s’annonce une fois et garde « Accepter l’invitation »', async () => {
      const { screen } = await renderAt('/amis', 1440, 900, {
        send: incoming,
        accept: () => Promise.reject(new NetworkError()),
      })

      await sendAndAccept(screen)

      await announcedOnce(screen, 'Connexion impossible. Vérifiez votre réseau puis réessayez.')
      await expect
        .element(screen.getByRole('button', { name: 'Accepter l’invitation' }))
        .toBeEnabled()
    })

    it('FRD-01, ADR 0038 (point 9) : pendant la réponse d’une ligne, « Accepter l’invitation » est inactif', async () => {
      const { screen } = await renderAt('/amis?onglet=invitations', 1440, 900, {
        send: incoming,
        accept: () => new Promise<void>(() => undefined),
      })
      await screen.getByLabelText('Identifiant').fill('sarah.belanger')
      await screen.getByRole('button', { name: 'Envoyer l’invitation' }).click()

      await screen.getByRole('button', { name: 'Accepter — Sarah Bélanger' }).click()

      await expect
        .element(screen.getByRole('button', { name: 'Accepter l’invitation' }))
        .toBeDisabled()
    })
  })

  it('Etats, Amis-Telephone : compte neuf, « Pas encore d’amis » et sa feuille ; aucun défilement horizontal', async () => {
    const { screen } = await renderAt('/amis', 390, 844, {
      friends: () => Promise.resolve({ items: [], next: null }),
    })

    await expect.element(screen.getByRole('heading', { name: 'Pas encore d’amis' })).toBeVisible()
    await screen.getByRole('button', { name: 'Ajouter un ami' }).last().click()
    await expect.element(screen.getByRole('dialog', { name: 'Ajouter un ami' })).toBeVisible()
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(390)
  })

  it('FRD-01 : pendant une réponse, les boutons des autres invitations sont désactivés', async () => {
    const { screen } = await renderAt('/amis?onglet=invitations', 820, 1180, {
      accept: () => new Promise<void>(() => undefined),
    })

    await screen.getByRole('button', { name: 'Accepter — Sarah Bélanger' }).click()

    await expect
      .element(screen.getByRole('button', { name: 'Accepter — Jade Morin' }))
      .toBeDisabled()
  })

  it('WCAG 2.4.3 : la confirmation fermée par Échap rend le focus au bouton « Plus d’actions »', async () => {
    const { screen } = await renderAt('/amis', 1440, 900)

    const more = screen.getByRole('button', { name: 'Plus d’actions — Inès Côté' })
    await more.click()
    await userEvent.keyboard('{Enter}')
    await expect.element(screen.getByRole('alertdialog')).toBeVisible()
    await userEvent.keyboard('{Escape}')

    await expect.element(more).toHaveFocus()
  })

  it('WCAG 2.4.3 : après un retrait réussi, le focus passe au titre « Amis »', async () => {
    let removed = false
    const { screen } = await renderAt('/amis', 1440, 900, {
      friends: () =>
        Promise.resolve({
          items: removed ? FRIENDS.filter((f) => f.displayName !== 'Inès Côté') : FRIENDS,
          next: null,
        }),
      unfriend: () => {
        removed = true
        return Promise.resolve()
      },
    })

    await screen.getByRole('button', { name: 'Plus d’actions — Inès Côté' }).click()
    await userEvent.keyboard('{Enter}')
    await screen.getByRole('button', { name: 'Retirer', exact: true }).click()

    await expect.element(screen.getByRole('heading', { level: 1, name: 'Amis' })).toHaveFocus()
  })

  it('WCAG 2.1.1 : le menu d’un ami se ferme au clic hors de lui et à Tab', async () => {
    const { screen } = await renderAt('/amis', 1440, 900)
    const more = screen.getByRole('button', { name: 'Plus d’actions — Inès Côté' })
    const item = screen.getByRole('menuitem', { name: 'Retirer des amis' })

    await more.click()
    await expect.element(item).toBeVisible()
    await screen.getByRole('heading', { level: 1, name: 'Amis' }).click()
    await expect.poll(() => item.elements().length).toBe(0)

    await more.click()
    await expect.element(item).toHaveFocus()
    await userEvent.keyboard('{Tab}')
    await expect.poll(() => item.elements().length).toBe(0)
  })

  it('A10 : onglet Invitations, une liste en échec donne l’état d’erreur ; « Réessayer » relit les deux listes', async () => {
    let receivedCalls = 0
    let sentCalls = 0
    const { screen } = await renderAt('/amis?onglet=invitations', 820, 1180, {
      receivedApi: () => {
        receivedCalls += 1
        return receivedCalls === 1
          ? Promise.reject(new NetworkError())
          : Promise.resolve({ items: RECEIVED, next: null })
      },
      sentApi: () => {
        sentCalls += 1
        return Promise.resolve({ items: SENT, next: null })
      },
    })

    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Sarah Bélanger')).toBeVisible()
    expect(receivedCalls).toBe(2)
    expect(sentCalls).toBe(2)
  })

  it('ADR 0038 (point 4) : dès 1024 px, l’aside « Ajouter un ami » est aussi sur l’onglet Invitations', async () => {
    const { screen } = await renderAt('/amis?onglet=invitations', 1440, 900)

    await expect
      .element(screen.getByRole('complementary', { name: 'Ajouter un ami' }))
      .toBeVisible()
  })

  it('ADR 0038 (point 4) : sur tablette, la feuille a la largeur du contenu et reste ouverte après un envoi', async () => {
    const { screen } = await renderAt('/amis', 820, 1180, {
      send: () =>
        Promise.resolve({ id: id(14), userId: id(24), username: 'noa.g', createdAt: new Date() }),
    })

    await screen.getByRole('button', { name: 'Ajouter un ami' }).first().click()
    const sheet = screen.getByRole('dialog', { name: 'Ajouter un ami' })
    await expect.element(sheet).toBeVisible()
    expect(sheet.element().getBoundingClientRect().width).toBe(
      document.querySelector('main')?.getBoundingClientRect().width,
    )
    await sheet.getByLabelText('Identifiant').fill('noa.g')
    await sheet.getByRole('button', { name: 'Envoyer l’invitation' }).click()

    await expect
      .poll(() => sheet.element().querySelector('[role="status"]')?.textContent)
      .toContain('Invitation envoyée à @noa.g.')
    await expect.element(sheet).toBeVisible()
  })

  it('A10 : une relecture en échec garde la liste déjà chargée', async () => {
    let calls = 0
    const { screen, client } = await renderAt('/amis', 1440, 900, {
      friends: () => {
        calls++
        return calls === 1
          ? Promise.resolve({ items: FRIENDS, next: null })
          : Promise.reject(new ApiError(503, 'INTERNAL_ERROR'))
      },
    })
    await expect.element(screen.getByText('Inès Côté')).toBeVisible()

    await client.refetchQueries({ queryKey: ['friends', 'list'] })
    await expect.poll(() => client.getQueryState(['friends', 'list'])?.status).toBe('error')
    // Laisse React rendre l'état d'erreur avant de vérifier qu'il n'a pas effacé la liste.
    await new Promise((resolve) => setTimeout(resolve, 100))

    await expect.element(screen.getByText('Inès Côté')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Réessayer' }).elements()).toHaveLength(0)
  })

  it('SEC-01, ADR 0024 : recherche bornée à 64 caractères, sans défilement horizontal à 390 px', async () => {
    const { screen } = await renderAt('/amis', 390, 844)

    const search = screen.getByRole('searchbox', { name: 'Rechercher un ami' })
    await search.fill('x'.repeat(80))

    expect((search.element() as HTMLInputElement).value).toHaveLength(64)
    await expect.element(screen.getByRole('button', { name: 'Effacer la recherche' })).toBeVisible()
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(390)
  })

  it('ADR 0038 (point 4) : la feuille s’ouvre le focus dans le champ Identifiant', async () => {
    const { screen } = await renderAt('/amis', 820, 1180)

    await screen.getByRole('button', { name: 'Ajouter un ami' }).click()

    await expect.element(screen.getByLabelText('Identifiant')).toHaveFocus()
  })

  it('A10, Etats : un échec de lecture propose « Réessayer »', async () => {
    const { screen } = await renderAt('/amis', 1440, 900, {
      friends: () => Promise.reject(new ApiError(500, 'INTERNAL_ERROR')),
    })

    await expect.element(screen.getByRole('button', { name: 'Réessayer' })).toBeVisible()
  })
})
