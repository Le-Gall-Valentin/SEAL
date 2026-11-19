import { describe, expect, it } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { withLock } from './withLock'
import { useHeldLock } from './useHeldLock'

describe('KEY-02, ADR 0029 (point 3) : verrou tenu tant qu’un écran est monté', () => {
  it('ADR 0029 : le second écran attend que le premier libère le verrou', async () => {
    const first = await renderHook(() => useHeldLock('seal-test-held'))
    await expect.poll(() => first.result.current.held).toBe(true)

    const second = await renderHook(() => useHeldLock('seal-test-held'))
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(second.result.current.held).toBe(false)

    first.result.current.release()
    await expect.poll(() => second.result.current.held).toBe(true)
    await second.unmount()
  })

  it('ADR 0029 : démonter l’écran libère le verrou, et une tâche qui l’attendait s’exécute', async () => {
    const held = await renderHook(() => useHeldLock('seal-test-held-2'))
    await expect.poll(() => held.result.current.held).toBe(true)

    const waiting = withLock('seal-test-held-2', () => Promise.resolve('après'))
    await held.unmount()

    await expect(waiting).resolves.toBe('après')
  })
})
