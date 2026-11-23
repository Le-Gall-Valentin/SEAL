import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { PASSWORD_INPUT_MAX_LENGTH } from '@/entities/password'
import { useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import { reauthFailureOf } from './reauthFailure'
import { useReauthApi } from './reauthApiPort'

/**
 * AUTH-55, ADR 0035 : ré-authentification par mot de passe. Une réussite prévient l'écran, qui relance l'action ; un
 * mauvais mot de passe vide le champ ; un 429 bloque le formulaire jusqu'à la fin de Retry-After.
 */
export function useReauthentication(onReauthenticated: () => void) {
  const api = useReauthApi()
  const [password, setPassword] = useState('')
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: (value: string) => api.reauthenticate(value),
    onSuccess: () => {
      setPassword('')
      onReauthenticated()
    },
    onError: (error) => {
      const kind = reauthFailureOf(error).kind
      if (kind === 'invalid-password' || kind === 'rate-limited') {
        setPassword('')
      }
    },
  })
  const { error, reset } = mutation
  const locked = useRetryAfterLock(error, reset)
  const ready = password !== '' && password.length <= PASSWORD_INPUT_MAX_LENGTH
  return {
    password,
    setPassword,
    failure: error === null ? null : reauthFailureOf(error),
    locked,
    pending: mutation.isPending,
    canSubmit: !locked && !mutation.isPending && ready,
    submit: () => {
      if (!locked && !mutation.isPending && ready) {
        guard.run(async () => {
          await mutation.mutateAsync(password)
          // CDC 3 : la mutation, et le mot de passe qu'elle porte, quittent le cache dès la réussite.
          mutation.reset()
        })
      }
    },
    /**
     * Fermeture du dialogue : rien de la saisie ni du refus ne reste pour la prochaine ouverture, et la mutation quitte
     * le cache ; sous un 429, elle reste jusqu'à la fin de Retry-After, qui tient le blocage.
     */
    clear: () => {
      setPassword('')
      if (!locked) {
        reset()
      }
    },
  }
}
