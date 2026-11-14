import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { isEmailWellFormed } from '@/entities/account'
import { useRetryAfterLock } from '@/shared/lib'
import { resetFailureOf } from './resetLink'
import { usePasswordResetApi } from './passwordResetApiContext'

/** AUTH-70 : demande d'un lien ; la réponse est la même que l'adresse ait un compte ou non. */
export function useForgotPassword() {
  const api = usePasswordResetApi()
  const [email, setEmail] = useState('')
  const [touched, setTouched] = useState(false)
  const [refused, setRefused] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const mutation = useMutation({
    mutationFn: (address: string) => api.request(address),
    onSuccess: () => {
      setSent(true)
    },
    onError: (error, address) => {
      if (resetFailureOf(error).kind === 'email') {
        setRefused(address)
      }
    },
  })
  const locked = useRetryAfterLock(mutation.error, mutation.reset)
  const failure = mutation.error === null ? null : resetFailureOf(mutation.error)
  const valid = isEmailWellFormed(email) && refused !== email
  const canSubmit = valid && !locked && !mutation.isPending
  return {
    email,
    setEmail,
    touch: () => {
      setTouched(true)
    },
    emailInvalid: (touched && email !== '' && !isEmailWellFormed(email)) || refused === email,
    alert:
      failure !== null &&
      (failure.kind === 'rate-limited' ||
        failure.kind === 'network' ||
        failure.kind === 'unexpected')
        ? failure
        : null,
    sent,
    canSubmit,
    submit: () => {
      if (canSubmit) {
        setSent(false)
        mutation.mutate(email)
      }
    },
  }
}
