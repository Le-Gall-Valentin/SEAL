import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createActivityGate } from './activityGate'

function act(): void {
  window.dispatchEvent(new PointerEvent('pointerdown'))
}

describe('shared/lib : garde d’activité (AUTH-52, ADR 0043 point 4)', () => {
  let now = 0
  beforeEach(() => {
    now = 1_000_000
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('AUTH-52 : après une action de moins d’une minute, la tâche part aussitôt', () => {
    const gate = createActivityGate({ now: () => now })
    const task = vi.fn()

    act()
    now += 59_000
    gate.run('amis', task)

    expect(task).toHaveBeenCalledOnce()
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
    expect(task).not.toHaveBeenCalled()

    act()
    act()

    expect(task).toHaveBeenCalledOnce()
    expect(replaced).not.toHaveBeenCalled()
    gate.dispose()
  })

  it('AUTH-52 : une page cachée attend aussi la prochaine action', () => {
    const gate = createActivityGate({ now: () => now, visible: () => false })
    const task = vi.fn()

    act()
    gate.run('amis', task)

    expect(task).not.toHaveBeenCalled()
    gate.dispose()
  })

  it('AUTH-52 : le défilement seul ne compte pas comme une action', () => {
    const gate = createActivityGate({ now: () => now })
    const task = vi.fn()

    gate.run('amis', task)
    window.dispatchEvent(new Event('scroll'))

    expect(task).not.toHaveBeenCalled()
    gate.dispose()
  })

  it('dispose() retire l’écoute : une action ne lance plus rien', () => {
    const gate = createActivityGate({ now: () => now })
    const task = vi.fn()

    gate.run('amis', task)
    gate.dispose()
    act()

    expect(task).not.toHaveBeenCalled()
  })
})
