import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { invalidateConversations } from '@/entities/conversation'
import { invalidateFriends } from '@/entities/friend'
import { type RealtimeEvent, useRealtime } from '@/entities/session'
import { type ActivityGate, createActivityGate } from '@/shared/lib'

/**
 * FRD-04, CNV-04, TRN-15 : les trames du serveur et chaque (re)connexion rendent périmées les listes concernées ; elles
 * sont relues aussitôt si l'utilisateur a agi depuis moins d'une minute, sinon à sa prochaine action ou au retour du
 * focus. Une page laissée ouverte expire donc même quand ses amis agissent (AUTH-52, ADR 0043 point 4).
 */
export function useRealtimeUpdates(): void {
  const client = useQueryClient()
  const gate = useRef<ActivityGate | null>(null)
  // Avant la connexion de useRealtime : les effets d'un composant partent dans l'ordre de leurs appels.
  useEffect(() => {
    const created = createActivityGate()
    gate.current = created
    return () => {
      created.dispose()
      gate.current = null
    }
  }, [])

  const friends = () => {
    void invalidateFriends(client, { refetch: false })
    gate.current?.run('friends', () => void invalidateFriends(client))
  }
  const conversations = () => {
    void invalidateConversations(client, { refetch: false })
    gate.current?.run('conversations', () => void invalidateConversations(client))
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
  })
}
