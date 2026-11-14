// Dans Chromium : sous Node, TanStack Query se croit côté serveur et ne rejoue jamais rien.
import { MutationObserver, QueryObserver } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { SESSION_QUERY_KEY } from '@/entities/session'
import { ApiError } from '@/shared/lib'
import { createQueryClient } from './queryClient'

describe('Racine de composition : client TanStack Query', () => {
  it("AUTH-16 : une lecture refusée n'est pas rejouée, l'écran suivant s'affiche sans attendre", async () => {
    let calls = 0
    const observer = new QueryObserver(createQueryClient(), {
      queryKey: ['compte'],
      queryFn: () => {
        calls += 1
        return Promise.reject(new Error('401'))
      },
    })

    const status = await new Promise<string>((resolve) => {
      const unsubscribe = observer.subscribe((result) => {
        if (result.status === 'error') {
          unsubscribe()
          resolve(result.status)
        }
      })
    })

    expect(status).toBe('error')
    expect(calls).toBe(1)
  })

  it("AUTH-20, SEC-10 : une mutation qui échoue n'est pas rejouée, pour ne pas consommer deux fois la limite de débit", async () => {
    let calls = 0
    const observer = new MutationObserver(createQueryClient(), {
      mutationFn: () => {
        calls += 1
        return Promise.reject(new Error('refus'))
      },
    })

    await expect(observer.mutate()).rejects.toThrow('refus')
    expect(calls).toBe(1)
  })

  it.each([
    ['UNAUTHENTICATED', null],
    ['INVALID_CREDENTIALS', 'compte'],
  ] as const)(
    'AUTH-53 : une mutation refusée en %s termine la session seulement si elle est perdue',
    async (code, expected) => {
      const client = createQueryClient()
      client.setQueryData(SESSION_QUERY_KEY, 'compte')
      const observer = new MutationObserver(client, {
        mutationFn: () => Promise.reject(new ApiError(401, code)),
      })

      await expect(observer.mutate()).rejects.toBeInstanceOf(ApiError)
      expect(client.getQueryData(SESSION_QUERY_KEY)).toBe(expected)
    },
  )
})
