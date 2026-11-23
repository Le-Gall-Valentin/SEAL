import { describe, expect, it } from 'vitest'
import { render } from 'vitest-browser-react'
import { ClockProvider } from '@/shared/lib'
import { RequestTime } from './RequestTime'

const NOW = Date.parse('2026-11-23T12:00:00Z')

async function shown(variant: 'received' | 'sent', at: string) {
  const screen = await render(
    <ClockProvider clock={{ now: () => NOW }}>
      <RequestTime variant={variant} date={new Date(at)} />
    </ClockProvider>,
  )
  return screen.container.querySelector('time')
}

describe('entities/friend : date d’une invitation (ADR 0038, point 2)', () => {
  it.each([
    ['received', '2026-11-23T10:00:00Z', 'Reçue il y a 2 heures'],
    ['received', '2026-11-23T11:59:00Z', 'Reçue il y a 1 minute'],
    ['received', '2026-11-23T11:59:50Z', 'Reçue à l’instant'],
    ['received', '2026-11-22T09:00:00Z', 'Reçue hier'],
    ['sent', '2026-11-20T12:00:00Z', 'Envoyée il y a 3 jours'],
    ['sent', '2026-11-12T12:00:00Z', 'Envoyée le 12 nov.'],
  ] as const)('ADR 0038 : %s le %s → « %s »', async (variant, at, text) => {
    const time = await shown(variant, at)

    expect(time?.textContent).toBe(text)
    expect(time?.getAttribute('datetime')).toBe(new Date(at).toISOString())
  })
})
