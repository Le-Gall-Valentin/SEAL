import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { invalidateFriends } from '@/entities/friend'
import { failureOf, type FormFailure, isFormFailure, useSubmitGuard } from '@/shared/lib'
import { useAnswerFriendRequestApi } from './answerFriendRequestPort'

type Action = 'accept' | 'decline' | 'revoke'

/** Issue d'une acceptation demandée par le formulaire d'invitation. */
export type AcceptResult = { readonly kind: 'accepted' } | { readonly kind: 'gone' } | FormFailure

/**
 * Section 10.1 : accepter, refuser, révoquer. Après chaque réponse, les listes et le compteur sont relus (ADR 0037,
 * point 2) ; une invitation close ailleurs (404) est annoncée (ADR 0038, point 2) et disparaît à la relecture.
 */
export function useAnswerFriendRequest() {
  const client = useQueryClient()
  const api = useAnswerFriendRequestApi()
  const [gone, setGone] = useState(false)
  // ADR 0033 (point 6) : verrou synchrone, une réponse à la fois, avant même le rendu suivant.
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: ({ action, requestId }: { action: Action; requestId: string }) =>
      api[action](requestId),
    onMutate: () => {
      setGone(false)
    },
    onSuccess: () => invalidateFriends(client),
    onError: async (error) => {
      const failure = failureOf(error)
      if (failure.kind === 'refused' && failure.code === 'NOT_FOUND') {
        setGone(true)
        await invalidateFriends(client)
      }
    },
  })
  const respond = (action: Action) => (requestId: string) => {
    guard.run(() => mutation.mutateAsync({ action, requestId }))
  }
  /** Acceptation depuis le formulaire après un 409 : l'issue lui est rendue (ADR 0038, point 8). */
  const acceptNow = (requestId: string): Promise<AcceptResult> =>
    new Promise((resolve) => {
      const started = guard.run(async () => {
        try {
          await mutation.mutateAsync({ action: 'accept', requestId })
          resolve({ kind: 'accepted' })
        } catch (error) {
          const failure = failureOf(error)
          resolve(
            failure.kind === 'refused' && failure.code === 'NOT_FOUND'
              ? { kind: 'gone' }
              : isFormFailure(failure)
                ? failure
                : { kind: 'unexpected' },
          )
        }
      })
      if (!started) {
        resolve({ kind: 'unexpected' })
      }
    })
  const failure = mutation.error === null || gone ? null : failureOf(mutation.error)
  const formFailure: FormFailure | null =
    failure === null ? null : isFormFailure(failure) ? failure : { kind: 'unexpected' }
  return {
    accept: respond('accept'),
    acceptNow,
    decline: respond('decline'),
    revoke: respond('revoke'),
    busyId: mutation.isPending ? mutation.variables.requestId : null,
    gone,
    formFailure,
  }
}
