import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { markConversationsStale, refetchConversations } from '@/entities/conversation'
import { markFriendsStale, refetchFriends } from '@/entities/friend'
import { type RealtimeEvent, useRealtime } from '@/entities/session'
import { type ActivityGate, createActivityGate, useClock } from '@/shared/lib'

/**
 * FRD-04, CNV-04, TRN-15 : les trames du serveur et chaque (re)connexion rendent périmées les listes concernées ; elles
 * sont relues si l'utilisateur a agi depuis moins d'une minute, sinon à sa prochaine action ou à son retour sur
 * l'onglet. Une connexion tombée ne rouvre qu'aux mêmes conditions. Une page laissée ouverte expire donc même quand
 * ses amis agissent (AUTH-52, ADR 0043 point 4, ADR 0044 décisions 1 et 2).
 */
export function useRealtimeUpdates(): void {
  const client = useQueryClient()
  const clock = useClock()
  const gate = useRef<ActivityGate | null>(null)
  // Avant la connexion de useRealtime : les effets d'un composant partent dans l'ordre de leurs appels.
  useEffect(() => {
    const created = createActivityGate({ now: () => clock.now() })
    gate.current = created
    return () => {
      created.dispose()
      gate.current = null
    }
  }, [clock])

  const friends = () => {
    void markFriendsStale(client)
    gate.current?.run('friends', () => void refetchFriends(client))
  }
  const conversations = () => {
    void markConversationsStale(client)
    gate.current?.run('conversations', () => void refetchConversations(client))
  }

  useRealtime({
    onEvent: (event: RealtimeEvent) => {
      switch (event.type) {
        case 'friend.request':
        case 'friend.update':
          friends()
          break
        case 'conv.update':
          conversations()
          break
      }
    },
    onOpen: () => {
      friends()
      conversations()
    },
    beforeReconnect: (resume) => {
      if (gate.current === null) {
        resume()
      } else {
        gate.current.run('reconnect', resume)
      }
    },
  })
}
