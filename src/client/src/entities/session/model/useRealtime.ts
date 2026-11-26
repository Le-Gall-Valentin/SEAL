import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useEffectEvent } from 'react'
import type { RealtimeEvent } from '../api/IRealtimeApi'
import { useRealtimeApi } from './realtimePort'
import { endSession, refreshSession } from './useCurrentAccount'

interface RealtimeHandlers {
  readonly onEvent: (event: RealtimeEvent) => void
  readonly onOpen: () => void
  /** ADR 0044 (décision 2) : quand rouvrir une connexion tombée ; aussitôt par défaut. */
  readonly beforeReconnect?: (resume: () => void) => void
}

/**
 * TRN-10, TRN-12 : une connexion temps réel tant que le composant est monté, sous la session complète d'un compte
 * actif. Sa fin côté serveur (AUTH-53) termine la session du client ; des refus (4429) la laissent, sans temps réel ;
 * une connexion injoignable fait relire /me (ADR 0044).
 */
export function useRealtime(handlers: RealtimeHandlers): void {
  const api = useRealtimeApi()
  const client = useQueryClient()
  const onEvent = useEffectEvent(handlers.onEvent)
  const onOpen = useEffectEvent(handlers.onOpen)
  const beforeReconnect = useEffectEvent((resume: () => void) => {
    if (handlers.beforeReconnect === undefined) {
      resume()
    } else {
      handlers.beforeReconnect(resume)
    }
  })
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
        beforeReconnect: (resume) => {
          beforeReconnect(resume)
        },
        // ADR 0044 (décision 2) : /me relu ; une session finie pendant la coupure répond 401, qui la termine.
        onUnreachable: () => {
          void refreshSession(client)
        },
      }),
    [api, client],
  )
}
