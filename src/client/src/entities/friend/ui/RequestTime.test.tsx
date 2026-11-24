import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { ClockProvider } from '@/shared/lib'
import { RequestTime } from './RequestTime'

/** Instants en heure locale : « hier » et « il y a 3 jours » se comptent en jours du calendrier local. */
const local = (day: number, hour: number, minute = 0) => new Date(2026, 10, day, hour, minute)
const NOW = local(23, 12).getTime()

async function shown(variant: 'received' | 'sent', at: Date, now: () => number = () => NOW) {
  const screen = await render(
    <ClockProvider clock={{ now }}>
      <RequestTime variant={variant} date={at} />
    </ClockProvider>,
  )
  return screen.container.querySelector('time')
}

describe('entities/friend : date d’une invitation (ADR 0038, point 2)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it.each([
    ['received', local(23, 10), 'Reçue il y a 2 heures'],
    ['received', local(23, 11), 'Reçue il y a 1 heure'],
    ['received', local(23, 11, 59), 'Reçue il y a 1 minute'],
    ['received', new Date(NOW - 10_000), 'Reçue à l’instant'],
    ['received', local(22, 9), 'Reçue hier'],
    ['sent', local(20, 12), 'Envoyée il y a 3 jours'],
    ['sent', local(12, 12), 'Envoyée le 12 nov.'],
  ] as const)('FRD-01, ADR 0038 : %s le %s → « %s »', async (variant, at, text) => {
    const time = await shown(variant, at)

    expect(time?.textContent).toBe(text)
    expect(time?.getAttribute('datetime')).toBe(at.toISOString())
  })

  it('FRD-01, ADR 0038 (point 2) : la date relative avance avec l’horloge, sans nouvelle lecture', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    let now = NOW
    const time = await shown('received', local(23, 11, 59), () => now)
    expect(time?.textContent).toBe('Reçue il y a 1 minute')

    now += 60_000
    vi.advanceTimersByTime(60_000)

    await expect.poll(() => time?.textContent).toBe('Reçue il y a 2 minutes')
  })
})
