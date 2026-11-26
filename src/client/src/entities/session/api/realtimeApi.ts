import { createRealtimeConnection, type SocketFactory } from '@/shared/ws'
import type { IRealtimeApi } from './IRealtimeApi'

/** TRN-10, TRN-02 : même origine que la page, donc même cookie de session et même hôte autorisé par la CSP. */
export function realtimeUrl(location: Pick<Location, 'protocol' | 'host'>): string {
  return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`
}

/** L'adaptateur du port ; les tests injectent une fausse WebSocket. */
export function createRealtimeApi(
  createSocket?: SocketFactory,
  location?: Pick<Location, 'protocol' | 'host'>,
): IRealtimeApi {
  return {
    connect(listener) {
      const connection = createRealtimeConnection({
        url: realtimeUrl(location ?? window.location),
        onFrame: listener.onEvent,
        onOpen: listener.onOpen,
        onEnd: listener.onEnd,
        beforeReconnect: listener.beforeReconnect,
        onUnreachable: listener.onUnreachable,
        ...(createSocket === undefined ? {} : { createSocket }),
      })
      return () => {
        connection.close()
      }
    },
  }
}

export const realtimeApi: IRealtimeApi = createRealtimeApi()
