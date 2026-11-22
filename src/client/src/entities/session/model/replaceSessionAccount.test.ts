import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import type { Account } from '../api/ISessionApi'
import { replaceSessionAccount, SESSION_QUERY_KEY } from './useCurrentAccount'

const OLD: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'ancienne@exemple.ca',
  status: 'ACTIVE',
  verification: null,
}

describe('ACC-02, ACC-03, ADR 0035 : compte rendu par une route du compte', () => {
  it('ACC-02 : une relecture de /me partie avant le changement ne remet pas l’ancien compte', async () => {
    const client = new QueryClient()
    client.setQueryData(SESSION_QUERY_KEY, OLD)
    let answer: (account: Account) => void = () => undefined
    const late = client.query({
      queryKey: SESSION_QUERY_KEY,
      queryFn: () =>
        new Promise<Account>((resolve) => {
          answer = resolve
        }),
      staleTime: 0,
    })

    replaceSessionAccount(client, { ...OLD, email: 'neuve@exemple.ca' })
    answer(OLD)
    await late.catch(() => undefined)

    expect(client.getQueryData<Account>(SESSION_QUERY_KEY)?.email).toBe('neuve@exemple.ca')
  })
})
