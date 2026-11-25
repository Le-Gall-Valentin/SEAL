import { describe, expect, it } from 'vitest'
import { realtimeUrl } from './realtimeApi'

describe('entities/session : adresse de la connexion temps réel (TRN-10)', () => {
  it('TRN-10 : wss:// sur la même origine que la page, chemin /ws', () => {
    expect(realtimeUrl({ protocol: 'https:', host: 'localhost:8443' })).toBe(
      'wss://localhost:8443/ws',
    )
  })

  it('TRN-10 : ws:// pour une page servie sans TLS (serveur de développement)', () => {
    expect(realtimeUrl({ protocol: 'http:', host: 'localhost:5173' })).toBe(
      'ws://localhost:5173/ws',
    )
  })
})
