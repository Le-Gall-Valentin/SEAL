import { describe, expect, it, vi } from 'vitest'
import { NetworkError } from '@/shared/lib'
import { createRealtimeConnection } from './connection'

describe('shared/ws : vraie WebSocket du navigateur (TRN-10, TRN-15)', () => {
  it('TRN-15 : une poignée de main impossible ferme la socket et programme une réouverture ; aucun envoi avant', async () => {
    const beforeReconnect = vi.fn()
    const connection = createRealtimeConnection({
      // Port 9 (discard) fermé sur la boucle locale : la poignée de main échoue aussitôt.
      url: 'ws://127.0.0.1:9/ws',
      onFrame: vi.fn(),
      onOpen: vi.fn(),
      onEnd: vi.fn(),
      beforeReconnect,
    })

    await expect(connection.send('msg.send', {})).rejects.toBeInstanceOf(NetworkError)
    await vi.waitFor(
      () => {
        expect(beforeReconnect).toHaveBeenCalledOnce()
      },
      { timeout: 5_000 },
    )
    connection.close()
  })
})
