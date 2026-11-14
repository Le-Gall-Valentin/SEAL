import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { refreshSession } from '@/entities/session'
import { useRetryAfterLock } from '@/shared/lib'
import type { Credentials } from '../api/ILoginApi'
import { canSubmit, loginAlertOf } from './loginAlert'
import { useLoginApi } from './loginApiPort'

/**
 * AUTH-20 à AUTH-22 : état du formulaire de connexion. Une réussite relit /me, et la garde de route mène à l'écran du
 * compte ; INVALID_CREDENTIALS vide le mot de passe ; un 429 bloque le formulaire jusqu'à la fin de Retry-After.
 */
export function useLoginForm() {
  const api = useLoginApi()
  const client = useQueryClient()
  const [values, setValues] = useState<Credentials>({ login: '', password: '' })
  const mutation = useMutation({
    mutationFn: (credentials: Credentials) => api.login(credentials),
    onSuccess: () => refreshSession(client),
    onError: (error) => {
      if (loginAlertOf(error).kind === 'invalid-credentials') {
        setValues((current) => ({ ...current, password: '' }))
      }
    },
  })
  const { error, reset } = mutation
  const locked = useRetryAfterLock(error, reset)
  return {
    values,
    setLogin: (login: string) => {
      setValues((current) => ({ ...current, login }))
    },
    setPassword: (password: string) => {
      setValues((current) => ({ ...current, password }))
    },
    alert: error === null ? null : loginAlertOf(error),
    locked,
    canSubmit: !locked && !mutation.isPending && canSubmit(values),
    submit: () => {
      if (!locked && !mutation.isPending && canSubmit(values)) {
        mutation.mutate(values)
      }
    },
  }
}
