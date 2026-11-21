import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { USERNAME_AVAILABILITY_KEY, useUsernameAvailability } from './useUsernameAvailability'
import {
  proposeUsername,
  statusAfterRefusal,
  type UsernameRefusal,
  usernameFromEmail,
} from './usernameProposal'

/**
 * AUTH-04, AUTH-05 : l'identifiant d'une inscription, classique ou OAuth (ADR 0033). Tant que l'utilisateur ne l'a pas
 * choisi, il suit la suggestion tirée du courriel, et la première suggestion libre remplace une base prise ou réservée
 * (étape 5). Un refus du serveur prime tant que la disponibilité ne l'a pas confirmé ; un identifiant pris fait relire
 * les disponibilités, que le temps a pu vieillir.
 */
export function useUsernameChoice(email: string) {
  const client = useQueryClient()
  const [chosen, setChosen] = useState<string | null>(null)
  const [refusal, setRefusal] = useState<UsernameRefusal | undefined>(undefined)
  const base = chosen ?? usernameFromEmail(email)
  const baseStatus = useUsernameAvailability(base)
  const proposal =
    chosen === null ? proposeUsername(base, baseStatus) : { username: base, status: baseStatus }
  return {
    username: proposal.username,
    status: statusAfterRefusal(proposal.status, refusal, proposal.username),
    /** L'identifiant suit encore la suggestion tirée du courriel. */
    fromEmail: chosen === null,
    choose: (value: string) => {
      setChosen(value)
    },
    refuse: (value: string, reason: UsernameRefusal['reason']) => {
      setRefusal({ value, reason })
      if (reason === 'taken') {
        void client.invalidateQueries({ queryKey: USERNAME_AVAILABILITY_KEY })
      }
    },
  }
}
