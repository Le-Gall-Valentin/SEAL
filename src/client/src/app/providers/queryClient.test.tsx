// Dans Chromium : sous Node, TanStack Query se croit côté serveur et ne rejoue jamais rien.
import { MutationObserver, QueryObserver } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
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
})
