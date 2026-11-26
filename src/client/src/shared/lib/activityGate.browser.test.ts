import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { COALESCE_MS, createActivityGate } from './activityGate'

function act(): void {
  window.dispatchEvent(new PointerEvent('pointerdown'))
}

describe('shared/lib : garde d’activité (AUTH-52, ADR 0043 point 4, ADR 0044)', () => {
  let now = 0
  beforeEach(() => {
    now = 1_000_000
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('AUTH-52 : après une action de moins d’une minute, la tâche part, après le court regroupement', () => {
    const gate = createActivityGate({ now: () => now })
    const task = vi.fn()

    act()
    now += 59_000
    gate.run('amis', task)
    expect(task).not.toHaveBeenCalled()
    vi.advanceTimersByTime(COALESCE_MS)

    expect(task).toHaveBeenCalledOnce()
    gate.dispose()
  })

  it('ADR 0044 : une rafale d’événements ne lance qu’une tâche par clé, la dernière', () => {
    const gate = createActivityGate({ now: () => now })
    const first = vi.fn()
    const last = vi.fn()
    const other = vi.fn()

    act()
    gate.run('amis', first)
    gate.run('conversations', other)
    gate.run('amis', last)
    vi.advanceTimersByTime(COALESCE_MS)

    expect(first).not.toHaveBeenCalled()
    expect(last).toHaveBeenCalledOnce()
    expect(other).toHaveBeenCalledOnce()
    gate.dispose()
  })

  it('AUTH-52 : sans action depuis une minute, la tâche attend la prochaine action, une seule fois par clé', () => {
    const gate = createActivityGate({ now: () => now })
    const task = vi.fn()
    const replaced = vi.fn()

    act()
    now += 60_000
    gate.run('amis', replaced)
    gate.run('amis', task)
    vi.advanceTimersByTime(COALESCE_MS)
    expect(task).not.toHaveBeenCalled()

    act()
    act()

    expect(task).toHaveBeenCalledOnce()
    expect(replaced).not.toHaveBeenCalled()
    gate.dispose()
  })

  it('AUTH-52, ADR 0044 : des gestes pendant le regroupement ne le contournent pas ; une seule tâche part', () => {
    const gate = createActivityGate({ now: () => now })
    const first = vi.fn()
    const last = vi.fn()

    act()
    gate.run('amis', first)
    window.dispatchEvent(new PointerEvent('pointermove'))
    gate.run('amis', last)
    window.dispatchEvent(new PointerEvent('pointermove'))
    expect(last).not.toHaveBeenCalled()
    vi.advanceTimersByTime(COALESCE_MS)

    expect(first).not.toHaveBeenCalled()
    expect(last).toHaveBeenCalledOnce()
    gate.dispose()
  })

  it('AUTH-52 : un focus posé sur un élément ne compte pas comme une action ; celui de la fenêtre, si', () => {
    const gate = createActivityGate({ now: () => now })
    const task = vi.fn()
    const button = document.createElement('button')
    document.body.append(button)

    gate.run('amis', task)
    button.dispatchEvent(new FocusEvent('focus'))
    vi.advanceTimersByTime(COALESCE_MS)
    expect(task).not.toHaveBeenCalled()

    window.dispatchEvent(new FocusEvent('focus'))
    expect(task).toHaveBeenCalledOnce()
    button.remove()
    gate.dispose()
  })

  it('AUTH-52, ADR 0044 : une page cachée attend ; revenir sur l’onglet vaut une action', () => {
    let visible = false
    const gate = createActivityGate({ now: () => now, visible: () => visible })
    const task = vi.fn()

    act()
    gate.run('amis', task)
    vi.advanceTimersByTime(COALESCE_MS)
    expect(task).not.toHaveBeenCalled()

    visible = true
    document.dispatchEvent(new Event('visibilitychange'))

    expect(task).toHaveBeenCalledOnce()
    gate.dispose()
  })

  it('AUTH-52 : le défilement seul ne compte pas comme une action', () => {
    const gate = createActivityGate({ now: () => now })
    const task = vi.fn()

    gate.run('amis', task)
    window.dispatchEvent(new Event('scroll'))
    vi.advanceTimersByTime(COALESCE_MS)

    expect(task).not.toHaveBeenCalled()
    gate.dispose()
  })

  it('AUTH-52 : dispose() retire toutes les écoutes : plus aucune action ni aucun retour ne lance de tâche', () => {
    const gate = createActivityGate({ now: () => now })
    const forgotten = vi.fn()
    const late = vi.fn()

    gate.run('amis', forgotten)
    gate.dispose()
    gate.run('amis', late)
    act()
    window.dispatchEvent(new FocusEvent('focus'))
    document.dispatchEvent(new Event('visibilitychange'))
    vi.advanceTimersByTime(COALESCE_MS)

    expect(forgotten).not.toHaveBeenCalled()
    expect(late).not.toHaveBeenCalled()
  })
})
