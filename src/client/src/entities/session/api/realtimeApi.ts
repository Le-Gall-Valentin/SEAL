import { createRealtimeConnection } from '@/shared/ws'
import type { IRealtimeApi } from './IRealtimeApi'

/** TRN-10, TRN-02 : même origine que la page, donc même cookie de session et même hôte autorisé par la CSP. */
export function realtimeUrl(location: Pick<Location, 'protocol' | 'host'>): string {
  return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`
}

export const realtimeApi: IRealtimeApi = {
  connect(listener) {
    const connection = createRealtimeConnection({
      url: realtimeUrl(window.location),
      onFrame: (event) => {
        if (event.type !== 'session.revoked') {
          listener.onEvent(event)
        }
      },
      onOpen: listener.onOpen,
      onEnd: listener.onEnd,
    })
    return () => {
      connection.close()
    }
  },
}
