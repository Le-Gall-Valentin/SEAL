import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ApiError } from '@/shared/lib'
import type { SentRequest } from '@/entities/friend'
import { SendFriendRequestApiProvider } from '../model/sendFriendRequestPort'
import { AddFriendForm } from './AddFriendForm'

const REQUEST = '0193a1b2-0000-7000-8000-00000000c0de'
const sent = {
  id: REQUEST,
  userId: '0192f7a1-0000-7000-8000-000000000001',
  username: 'sarah.belanger',
  createdAt: new Date(),
}

async function mount(send: (username: string) => Promise<SentRequest>, onAccept = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const screen = await render(
    <QueryClientProvider client={client}>
      <SendFriendRequestApiProvider api={{ send }}>
        <AddFriendForm onAccept={onAccept} accepting={false} />
      </SendFriendRequestApiProvider>
    </QueryClientProvider>,
  )
  return { screen, client, onAccept }
}

async function invite(screen: Awaited<ReturnType<typeof mount>>['screen'], input: string) {
  await screen.getByLabelText('Identifiant').fill(input)
  await screen.getByRole('button', { name: 'Envoyer l’invitation' }).click()
}

describe('features/send-friend-request : formulaire « Ajouter un ami » (Amis-Ordinateur, ADR 0038)', () => {
  it('FRD-01, ADR 0038 (point 1) : invitation envoyée, identifiant normalisé, champ vidé, listes relues', async () => {
    const send = vi.fn(() => Promise.resolve(sent))
    const { screen, client } = await mount(send)
    const invalidate = vi.spyOn(client, 'invalidateQueries')

    await invite(screen, ' @Sarah.Belanger ')

    await expect
      .element(screen.getByRole('status'))
      .toHaveTextContent('Invitation envoyée à @sarah.belanger.')
    expect(send).toHaveBeenCalledWith('sarah.belanger')
    await expect.element(screen.getByLabelText('Identifiant')).toHaveValue('')
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['friends'] })
  })

  it('ADR 0038 (point 1) : une saisie hors AUTH-01 est refusée sans requête', async () => {
    const send = vi.fn(() => Promise.resolve(sent))
    const { screen } = await mount(send)

    await invite(screen, 'élodie')

    await expect
      .element(
        screen.getByText('3 à 20 caractères : minuscules, chiffres, et « . » ou « _ » entre deux.'),
      )
      .toBeVisible()
    expect(send).not.toHaveBeenCalled()
  })

  it('FRD-02, ADR 0038 (point 1) : identifiant inexistant, annoncé en alerte', async () => {
    const { screen } = await mount(() => Promise.reject(new ApiError(404, 'USER_NOT_FOUND')))

    await invite(screen, 'noa.g')

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Aucun utilisateur ne porte l’identifiant @noa.g.')
  })

  it.each([
    ['CANNOT_INVITE_SELF', 422, 'Vous ne pouvez pas vous inviter vous-même'],
    ['ALREADY_FRIENDS', 409, 'Vous êtes déjà amis'],
    ['REQUEST_ALREADY_PENDING', 409, 'Invitation déjà envoyée'],
    ['CANNOT_INVITE', 403, 'Impossible d’inviter cet utilisateur'],
  ] as const)('FRD-01, FRD-03 : %s affiche « %s »', async (code, status, text) => {
    const { screen } = await mount(() => Promise.reject(new ApiError(status, code)))

    await invite(screen, 'sarah.belanger')

    await expect.element(screen.getByRole('alert')).toHaveTextContent(text)
  })

  it('FRD-01, ADR 0038 (point 8) : « Accepter l’invitation » accepte l’invitation désignée par le 409', async () => {
    const { screen, onAccept } = await mount(() =>
      Promise.reject(new ApiError(409, 'INCOMING_REQUEST_EXISTS', { requestId: REQUEST })),
    )

    await invite(screen, 'sarah.belanger')

    await expect
      .element(screen.getByRole('button', { name: 'Accepter l’invitation' }))
      .toBeVisible()
    expect(screen.getByRole('status').element().textContent).toContain(
      '@sarah.belanger vous a déjà invité. Acceptez directement !',
    )
    await screen.getByRole('button', { name: 'Accepter l’invitation' }).click()
    expect(onAccept).toHaveBeenCalledWith(REQUEST)
  })

  it('SEC-10 : un 429 annonce le délai et bloque l’envoi', async () => {
    const { screen } = await mount(() =>
      Promise.reject(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 300 })),
    )

    await invite(screen, 'sarah.belanger')

    await expect
      .element(screen.getByText('Réessayez dans 5 minutes.', { exact: false }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: 'Envoyer l’invitation' }))
      .toBeDisabled()
  })
})
