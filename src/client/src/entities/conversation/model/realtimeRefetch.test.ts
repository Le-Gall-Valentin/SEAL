import { QueryClient, QueryObserver } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { CONVERSATIONS_KEY, markConversationsStale, refetchConversations } from './useConversations'

describe('entities/conversation : relecture sur trame (ADR 0044, décision 1)', () => {
  it('CNV-04, TRN-15 : une trame arrivée pendant une lecture en cours relance la lecture ; rien n’est perdu', async () => {
    const client = new QueryClient()
    const resolvers: ((value: string[]) => void)[] = []
    const observer = new QueryObserver(client, {
      queryKey: CONVERSATIONS_KEY,
      queryFn: () =>
        new Promise<string[]>((resolve) => {
          resolvers.push(resolve)
        }),
    })
    const unsubscribe = observer.subscribe(() => undefined)
    await vi.waitFor(() => {
      expect(resolvers).toHaveLength(1)
    })
    resolvers[0]?.(['ancienne'])
    await vi.waitFor(() => {
      expect(client.getQueryData(CONVERSATIONS_KEY)).toEqual(['ancienne'])
    })
    // Une relecture part (retour du focus, action), avant que le serveur ne valide le changement annoncé.
    void observer.refetch()
    await vi.waitFor(() => {
      expect(resolvers).toHaveLength(2)
    })

    await markConversationsStale(client)
    const refetched = refetchConversations(client)
    await vi.waitFor(() => {
      expect(resolvers).toHaveLength(3)
    })
    resolvers[1]?.(['ancienne'])
    resolvers[2]?.(['ancienne', 'annoncée'])
    await refetched

    expect(client.getQueryData(CONVERSATIONS_KEY)).toEqual(['ancienne', 'annoncée'])
    unsubscribe()
  })
})
