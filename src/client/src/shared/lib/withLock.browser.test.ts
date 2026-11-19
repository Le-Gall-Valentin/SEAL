import { describe, expect, it } from 'vitest'
import { withLock } from './withLock'

describe('KEY-02, KEY-33, ADR 0029 (point 3) : verrou entre les onglets (Web Locks)', () => {
  it('ADR 0029 : deux tâches du même verrou s’exécutent l’une après l’autre, et rendent leur résultat', async () => {
    const steps: string[] = []
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })

    const first = withLock('seal-test-lock', async () => {
      steps.push('premier : début')
      await gate
      steps.push('premier : fin')
      return 1
    })
    const second = withLock('seal-test-lock', () => {
      steps.push('second')
      return Promise.resolve(2)
    })
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(steps).toEqual(['premier : début'])
    release()

    await expect(Promise.all([first, second])).resolves.toEqual([1, 2])
    expect(steps).toEqual(['premier : début', 'premier : fin', 'second'])
  })

  it('A10 : une tâche qui échoue libère le verrou et rend son erreur', async () => {
    await expect(
      withLock('seal-test-lock-2', () => Promise.reject(new Error('échec'))),
    ).rejects.toThrow('échec')
    await expect(withLock('seal-test-lock-2', () => Promise.resolve('libre'))).resolves.toBe(
      'libre',
    )
  })

  it('ADR 0029, point 13 : une tâche qui attend le verrou tenu ailleurs le signale, une tâche qui l’obtient non', async () => {
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const waits: string[] = []
    const holder = withLock('seal-test-lock-3', () => gate)

    await expect(
      withLock(
        'seal-test-lock-4',
        () => Promise.resolve('libre'),
        () => waits.push('libre'),
      ),
    ).resolves.toBe('libre')
    const waiting = withLock(
      'seal-test-lock-3',
      () => Promise.resolve('après'),
      () => waits.push('attente'),
    )
    await expect.poll(() => waits).toEqual(['attente'])
    release()

    await expect(waiting).resolves.toBe('après')
    await holder
  })
})
