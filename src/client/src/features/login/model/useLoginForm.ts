import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { refreshSession } from '@/entities/session'
import { ApiError } from '@/shared/lib'
import type { Credentials } from '../api/ILoginApi'
import { canSubmit, loginAlertOf } from './loginAlert'
import { useLoginApi } from './loginApiContext'

/** SEC-10 : un 429 sans Retry-After lisible bloque le formulaire une minute. */
const DEFAULT_LOCK_SECONDS = 60

function lockSeconds(error: unknown): number | null {
  return error instanceof ApiError && error.code === 'RATE_LIMITED'
    ? (error.retryAfterSeconds ?? DEFAULT_LOCK_SECONDS)
    : null
}

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
  const lock = lockSeconds(error)

  useEffect(() => {
    if (lock === null) {
      return undefined
    }
    const timer = setTimeout(reset, lock * 1000)
    return () => {
      clearTimeout(timer)
    }
  }, [lock, reset])

  const locked = lock !== null
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
