import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { useActivityGatedInterval } from './useActivityGatedInterval'

const PERIOD = 60_000

describe('shared/lib : échéance conditionnée par l’activité (AUTH-52, ADR 0037 point 11)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('AUTH-52 : sans action de l’utilisateur, aucune échéance ne rappelle', async () => {
    const tick = vi.fn()
    await renderHook(() => {
      useActivityGatedInterval(tick, PERIOD)
    })

    vi.advanceTimersByTime(3 * PERIOD)

    expect(tick).not.toHaveBeenCalled()
  })

  it('AUTH-52 : une action ne vaut que pour l’échéance suivante', async () => {
    const tick = vi.fn()
    await renderHook(() => {
      useActivityGatedInterval(tick, PERIOD)
    })

    window.dispatchEvent(new KeyboardEvent('keydown'))
    vi.advanceTimersByTime(PERIOD - 1)
    expect(tick).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(tick).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(2 * PERIOD)
    expect(tick).toHaveBeenCalledTimes(1)
  })

  it.each(['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart'])(
    'AUTH-52 : « %s » compte comme une action',
    async (type) => {
      const tick = vi.fn()
      await renderHook(() => {
        useActivityGatedInterval(tick, PERIOD)
      })

      window.dispatchEvent(new Event(type))
      vi.advanceTimersByTime(PERIOD)

      expect(tick).toHaveBeenCalledTimes(1)
    },
  )

  it('AUTH-52 : un défilement que la page provoque seule (recalage, scrollIntoView) ne compte pas', async () => {
    const tick = vi.fn()
    await renderHook(() => {
      useActivityGatedInterval(tick, PERIOD)
    })
    const list = document.createElement('div')
    document.body.append(list)

    list.dispatchEvent(new Event('scroll'))
    window.dispatchEvent(new Event('scroll'))
    vi.advanceTimersByTime(PERIOD)

    expect(tick).not.toHaveBeenCalled()
    list.remove()
  })

  it('AUTH-52 : onglet caché, l’échéance ne rappelle pas', async () => {
    const tick = vi.fn()
    await renderHook(() => {
      useActivityGatedInterval(tick, PERIOD)
    })
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    try {
      window.dispatchEvent(new KeyboardEvent('keydown'))
      vi.advanceTimersByTime(PERIOD)
      expect(tick).not.toHaveBeenCalled()
    } finally {
      Reflect.deleteProperty(document, 'visibilityState')
    }
  })

  it('AUTH-52 : démonté, plus aucune échéance ni écoute', async () => {
    const tick = vi.fn()
    const { unmount } = await renderHook(() => {
      useActivityGatedInterval(tick, PERIOD)
    })

    await unmount()
    window.dispatchEvent(new KeyboardEvent('keydown'))
    vi.advanceTimersByTime(PERIOD)

    expect(tick).not.toHaveBeenCalled()
  })
})
