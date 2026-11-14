import { describe, expect, it } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { ClockProvider, useClock } from './clock'

describe('Horloge injectée', () => {
  it('sans fournisseur, l’horloge du système', async () => {
    const before = Date.now()
    const { result } = await renderHook(() => useClock())

    expect(result.current.now()).toBeGreaterThanOrEqual(before)
  })

  it('avec un fournisseur, l’horloge fournie', async () => {
    const { result } = await renderHook(() => useClock(), {
      wrapper: ({ children }) => (
        <ClockProvider clock={{ now: () => 42 }}>{children}</ClockProvider>
      ),
    })

    expect(result.current.now()).toBe(42)
  })
})
