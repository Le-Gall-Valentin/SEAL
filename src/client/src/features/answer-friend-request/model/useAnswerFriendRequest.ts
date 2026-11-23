import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { invalidateFriends } from '@/entities/friend'
import { failureOf, type FormFailure, isFormFailure } from '@/shared/lib'
import { useAnswerFriendRequestApi } from './answerFriendRequestPort'

type Action = 'accept' | 'decline' | 'revoke'

/**
 * Section 10.1 : accepter, refuser, révoquer. Après chaque réponse, les listes et le compteur sont relus (ADR 0037,
 * point 2) ; une invitation close ailleurs (404) est annoncée (ADR 0038, point 2) et disparaît à la relecture.
 */
export function useAnswerFriendRequest() {
  const client = useQueryClient()
  const api = useAnswerFriendRequestApi()
  const [gone, setGone] = useState(false)
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
  const run = (action: Action) => (requestId: string) => {
    if (!mutation.isPending) {
      mutation.mutate({ action, requestId })
    }
  }
  const failure = mutation.error === null || gone ? null : failureOf(mutation.error)
  const formFailure: FormFailure | null =
    failure === null ? null : isFormFailure(failure) ? failure : { kind: 'unexpected' }
  return {
    accept: run('accept'),
    decline: run('decline'),
    revoke: run('revoke'),
    busyId: mutation.isPending ? mutation.variables.requestId : null,
    gone,
    formFailure,
  }
}
