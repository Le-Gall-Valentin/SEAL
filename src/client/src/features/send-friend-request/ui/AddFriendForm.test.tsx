import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
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

type AcceptResult = Awaited<ReturnType<ComponentProps<typeof AddFriendForm>['onAccept']>>

async function mount(
  send: (username: string) => Promise<SentRequest>,
  onAccept = vi.fn((): Promise<AcceptResult> => Promise.resolve({ kind: 'accepted' })),
) {
  const onAccepted = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const screen = await render(
    <QueryClientProvider client={client}>
      <SendFriendRequestApiProvider api={{ send }}>
        <AddFriendForm onAccept={onAccept} onAccepted={onAccepted} />
      </SendFriendRequestApiProvider>
    </QueryClientProvider>,
  )
  return { screen, client, onAccept, onAccepted }
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

  it('ADR 0038 (point 1) : la région de statut existe avant tout envoi, pour être annoncée', async () => {
    const { screen } = await mount(() => Promise.resolve(sent))

    expect(screen.container.querySelector('[role="status"]')?.textContent).toBe('')
  })

  it('FRD-01, ADR 0038 : l’invitation acceptée depuis le formulaire efface le bloc et le signale à la page', async () => {
    const { screen, onAccepted } = await mount(() =>
      Promise.reject(new ApiError(409, 'INCOMING_REQUEST_EXISTS', { requestId: REQUEST })),
    )

    await invite(screen, 'sarah.belanger')
    await screen.getByRole('button', { name: 'Accepter l’invitation' }).click()

    await expect.poll(() => onAccepted.mock.calls.length).toBe(1)
    expect(screen.getByRole('button', { name: 'Accepter l’invitation' }).elements()).toHaveLength(0)
    expect(screen.container.querySelector('[role="status"]')?.textContent).toBe('')
  })

  it('ADR 0038 (point 2) : une invitation close entre-temps l’annonce dans le formulaire', async () => {
    const { screen } = await mount(
      () => Promise.reject(new ApiError(409, 'INCOMING_REQUEST_EXISTS', { requestId: REQUEST })),
      vi.fn((): Promise<AcceptResult> => Promise.resolve({ kind: 'gone' })),
    )

    await invite(screen, 'sarah.belanger')
    await screen.getByRole('button', { name: 'Accepter l’invitation' }).click()

    await expect
      .element(screen.getByRole('alert'))
      .toHaveTextContent('Cette invitation n’existe plus.')
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

  describe('ADR 0038 (point 9) : mesures et jetons d’Amis-Ordinateur et d’Amis-Telephone', () => {
    it('Amis-Ordinateur : 6 px entre « @ » et la saisie', async () => {
      const { screen } = await mount(vi.fn())

      const field = screen.getByLabelText('Identifiant').element()

      expect(field.parentElement && getComputedStyle(field.parentElement).columnGap).toBe('6px')
    })

    it('Amis-Ordinateur : bloc de succès bordé d’olive, hauteur de ligne 1,35', async () => {
      const { screen } = await mount(() => Promise.resolve(sent))

      await invite(screen, 'sarah.belanger')
      const status = screen.container.querySelector('[role="status"]')
      await expect.poll(() => status?.textContent).toContain('Invitation envoyée')

      const notice = status?.firstElementChild
      expect(notice && getComputedStyle(notice).boxShadow).toContain('rgb(214, 221, 186)')
      expect(notice && getComputedStyle(notice).lineHeight).toBe('17.55px')
    })

    it('Amis-Telephone : refus en alerte, écart de 12 px, hauteur de ligne 1,35', async () => {
      const { screen } = await mount(() => Promise.reject(new ApiError(404, 'USER_NOT_FOUND')))

      await invite(screen, 'noa.g')
      const alert = screen.getByRole('alert')
      await expect.element(alert).toBeVisible()

      expect(getComputedStyle(alert.element()).columnGap).toBe('12px')
      expect(getComputedStyle(alert.element()).lineHeight).toBe('17.55px')
    })
  })
})
