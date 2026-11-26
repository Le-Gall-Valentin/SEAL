import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useEffectEvent } from 'react'
import type { RealtimeEvent } from '../api/IRealtimeApi'
import { useRealtimeApi } from './realtimePort'
import { useSessionApi } from './sessionApiPort'
import { endSession, SESSION_QUERY_KEY } from './useCurrentAccount'

interface RealtimeHandlers {
  readonly onEvent: (event: RealtimeEvent) => void
  readonly onOpen: () => void
  /**
   * ADR 0044 (décision 2) : quand rouvrir une connexion tombée ; obligatoire, car une poignée de main prolonge la
   * session (AUTH-52).
   */
  readonly beforeReconnect: (resume: () => void) => void
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
  const beforeReconnect = useEffectEvent(handlers.beforeReconnect)
  const sessionApi = useSessionApi()
  // ADR 0044 (décision 2), AUTH-53 : /me relu ; une session finie pendant la coupure (null) est terminée, et les
  // données lues sous le compte oubliées. Une erreur réseau laisse la session : la reconnexion continue.
  const unreachable = useEffectEvent(() => {
    client
      .query({
        queryKey: SESSION_QUERY_KEY,
        queryFn: ({ signal }) => sessionApi.me(signal),
        staleTime: 0,
      })
      .then(
        (account) => {
          if (account === null) {
            endSession(client)
          }
        },
        () => undefined,
      )
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
        onUnreachable: () => {
          unreachable()
        },
      }),
    [api, client],
  )
}
