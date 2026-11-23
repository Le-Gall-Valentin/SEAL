import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { invalidateFriends } from '@/entities/friend'
import { isFormFailure, useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import { normalizeInvitedUsername } from './invitedUsername'
import {
  type SendFriendRequestFailure,
  sendFriendRequestFailureOf,
} from './sendFriendRequestFailure'
import { useSendFriendRequestApi } from './sendFriendRequestPort'

/** Issue affichée sous le formulaire : l'invitation partie, ou un refus de 10.1, avec l'identifiant visé. */
export type SendOutcome =
  | { readonly kind: 'sent'; readonly username: string }
  | (Exclude<
      SendFriendRequestFailure,
      { readonly kind: 'network' | 'rate-limited' | 'unexpected' }
    > & {
      readonly username: string
    })

/**
 * FRD-01 à FRD-03 : formulaire « Ajouter un ami ». Une saisie hors AUTH-01 ne part pas ; une réussite vide le champ
 * et relit les listes (ADR 0037, point 2) ; un 429 bloque l'envoi jusqu'à `Retry-After`.
 */
export function useSendFriendRequest() {
  const client = useQueryClient()
  const api = useSendFriendRequestApi()
  const guard = useSubmitGuard()
  const [value, setValue] = useState('')
  const [formatInvalid, setFormatInvalid] = useState(false)
  const [outcome, setOutcome] = useState<SendOutcome | null>(null)
  const mutation = useMutation({
    mutationFn: (username: string) => api.send(username),
    onSuccess: async (request) => {
      setValue('')
      setOutcome({ kind: 'sent', username: request.username })
      await invalidateFriends(client)
    },
    onError: (error, username) => {
      const failure = sendFriendRequestFailureOf(error)
      setOutcome(isFormFailure(failure) ? null : { ...failure, username })
    },
  })
  const { error, reset } = mutation
  const locked = useRetryAfterLock(error, reset)
  const failure = error === null ? null : sendFriendRequestFailureOf(error)
  return {
    value,
    setValue: (next: string) => {
      setValue(next)
      setFormatInvalid(false)
    },
    formatInvalid,
    outcome,
    formFailure: failure !== null && isFormFailure(failure) ? failure : null,
    locked,
    pending: mutation.isPending,
    submit: () => {
      if (locked || mutation.isPending) {
        return
      }
      const username = normalizeInvitedUsername(value)
      setOutcome(null)
      if (username === null) {
        setFormatInvalid(true)
        return
      }
      guard.run(() => mutation.mutateAsync(username))
    },
  }
}
