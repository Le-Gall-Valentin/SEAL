import { MutationObserver, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { render, renderHook } from 'vitest-browser-react'
import type { Account, ISessionApi } from '../api/ISessionApi'
import { SessionApiProvider, useSessionApi } from './sessionApiPort'
import { endSession, SESSION_QUERY_KEY, useCurrentAccount } from './useCurrentAccount'

const ACCOUNT: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@exemple.ca',
  status: 'ACTIVE',
  verification: null,
}

function fakeApi(account: Account | null): ISessionApi {
  return { me: () => Promise.resolve(account), reloadCsrf: () => Promise.resolve() }
}

function wrapper(client: QueryClient, api: ISessionApi) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <SessionApiProvider api={api}>{children}</SessionApiProvider>
      </QueryClientProvider>
    )
  }
}

describe('entities/session : compte courant', () => {
  it('ADR 0024 : useCurrentAccount lit le compte par le port injecté', async () => {
    const client = new QueryClient()
    const { result } = await renderHook(() => useCurrentAccount(), {
      wrapper: wrapper(client, fakeApi(ACCOUNT)),
    })

    await expect.poll(() => result.current.data).toEqual(ACCOUNT)
  })

  it('ADR 0024 : sans fournisseur, le port manquant est signalé clairement', async () => {
    function Orphan() {
      useSessionApi()
      return null
    }

    await expect(render(<Orphan />)).rejects.toThrow('SessionApiProvider')
  })

  it("AUTH-53 : la fin de session rend l'état anonyme et oublie les données du compte", () => {
    const client = new QueryClient()
    client.setQueryData(SESSION_QUERY_KEY, ACCOUNT)
    client.setQueryData(['conversations'], ['secret'])

    endSession(client)

    expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull()
    expect(client.getQueryData(['conversations'])).toBeUndefined()
  })

  it('AUTH-53 : la fin de session oublie aussi les mutations, et les mots de passe qu’elles portaient', async () => {
    const client = new QueryClient()
    await new MutationObserver(client, {
      mutationFn: (credentials: { password: string }) =>
        Promise.resolve(credentials.password.length),
    }).mutate({ password: 'fjord-tuque-raquette' })
    expect(client.getMutationCache().getAll()).toHaveLength(1)

    endSession(client)

    expect(client.getMutationCache().getAll()).toHaveLength(0)
  })

  it('AUTH-16 : un compte lu récemment n’est pas relu à chaque écran monté', async () => {
    let calls = 0
    const api: ISessionApi = {
      me: () => {
        calls += 1
        return Promise.resolve(ACCOUNT)
      },
      reloadCsrf: () => Promise.resolve(),
    }
    const client = new QueryClient()
    const first = await renderHook(() => useCurrentAccount(), { wrapper: wrapper(client, api) })
    await expect.poll(() => first.result.current.data).toEqual(ACCOUNT)

    const second = await renderHook(() => useCurrentAccount(), { wrapper: wrapper(client, api) })

    expect(second.result.current.data).toEqual(ACCOUNT)
    expect(calls).toBe(1)
  })
})
