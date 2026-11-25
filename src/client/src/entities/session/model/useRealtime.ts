import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useEffectEvent } from 'react'
import type { RealtimeEvent } from '../api/IRealtimeApi'
import { useRealtimeApi } from './realtimePort'
import { endSession } from './useCurrentAccount'

interface RealtimeHandlers {
  readonly onEvent: (event: RealtimeEvent) => void
  readonly onOpen: () => void
}

/**
 * TRN-10, TRN-12 : une connexion temps réel tant que le composant est monté, sous la session complète d'un compte
 * actif. Sa fin côté serveur (AUTH-53) termine la session du client ; des refus (4429) la laissent, sans temps réel.
 */
export function useRealtime(handlers: RealtimeHandlers): void {
  const api = useRealtimeApi()
  const client = useQueryClient()
  const onEvent = useEffectEvent(handlers.onEvent)
  const onOpen = useEffectEvent(handlers.onOpen)
  useEffect(
    () =>
      api.connect({
        onEvent: (event) => {
          onEvent(event)
        },
        onOpen: () => {
          onOpen()
        },
        onEnd: (reason) => {
          if (reason === 'session') {
            endSession(client)
          }
        },
      }),
    [api, client],
  )
}
