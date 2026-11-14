import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { isEmailWellFormed } from '@/entities/account'
import { type Account, refreshSession } from '@/entities/session'
import { ApiError, useRetryAfterLock } from '@/shared/lib'
import { CODE_LENGTH, verifyFailureOf, type VerifyFailure } from './code'
import { useVerifyEmailApi } from './verifyEmailApiPort'
import { useSecondsLeft } from './useSecondsLeft'

const EMPTY_CODE: readonly string[] = Array.from({ length: CODE_LENGTH }, () => '')

interface VerificationProps {
  readonly email: string
  readonly verification: Account['verification']
}

/**
 * AUTH-14, AUTH-17 : vérification du code, renvoi et changement du courriel en attente. Une seule action à la fois
 * affiche son erreur ; un 429 suspend les trois jusqu'à la fin de Retry-After. Chaque réussite relit /me : le compte
 * à rebours et les renvois restants en viennent, et la garde mène à l'accueil une fois le compte actif.
 */
export function useEmailVerification(
  { email, verification }: VerificationProps,
  onCodeCleared: () => void,
) {
  const api = useVerifyEmailApi()
  const client = useQueryClient()
  const [cells, setCells] = useState<readonly string[]>(EMPTY_CODE)
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [changing, setChanging] = useState(false)
  const [newEmail, setNewEmail] = useState('')
  const [newEmailTouched, setNewEmailTouched] = useState(false)
  const [refusedEmail, setRefusedEmail] = useState<string | null>(null)

  const verify = useMutation({
    mutationFn: (code: string) => api.verify(code),
    onSuccess: () => refreshSession(client),
    onError: (error) => {
      if (verifyFailureOf(error).kind === 'invalid-code') {
        setCells(EMPTY_CODE)
        onCodeCleared()
      }
    },
  })
  const resend = useMutation({
    mutationFn: () => api.resend(),
    onSuccess: async () => {
      setSentTo(email)
      await refreshSession(client)
    },
  })
  const change = useMutation({
    mutationFn: (address: string) => api.changeEmail(address),
    onSuccess: async (_, address) => {
      setCells(EMPTY_CODE)
      setChanging(false)
      setNewEmail('')
      setNewEmailTouched(false)
      setSentTo(address)
      await refreshSession(client)
    },
    onError: (error, address) => {
      if (error instanceof ApiError && error.fields.includes('email')) {
        setRefusedEmail(address)
      }
    },
  })

  const error = verify.error ?? resend.error ?? change.error
  const resetAll = () => {
    verify.reset()
    resend.reset()
    change.reset()
  }
  const locked = useRetryAfterLock(error, resetAll)
  const busy = locked || verify.isPending || resend.isPending || change.isPending
  const seconds = useSecondsLeft(verification?.codeExpiresAt ?? null)
  const expired = seconds === null || seconds === 0
  const failure = error === null ? null : verifyFailureOf(error)
  // Un refus de l'adresse s'affiche sous son champ, pas en alerte.
  const fieldRefusal = error instanceof ApiError && error.fields.includes('email')
  const alert: VerifyFailure | null =
    failure !== null && !fieldRefusal ? failure : expired ? { kind: 'expired' } : null
  const code = cells.join('')
  const resendsRemaining = verification?.resendsRemaining ?? 0

  /** Une action efface l'erreur et la confirmation de la précédente. */
  const start = () => {
    resetAll()
    setSentTo(null)
  }

  return {
    cells,
    setCells,
    secondsLeft: seconds ?? 0,
    alert,
    locked,
    canVerify: code.length === CODE_LENGTH && !expired && !busy,
    verify: () => {
      if (code.length === CODE_LENGTH && !expired && !busy) {
        start()
        verify.mutate(code)
      }
    },
    resendsRemaining,
    canResend: resendsRemaining > 0 && !busy,
    resend: () => {
      if (resendsRemaining > 0 && !busy) {
        start()
        resend.mutate()
      }
    },
    sentTo,
    changing,
    openChange: () => {
      setChanging(true)
    },
    cancelChange: () => {
      setChanging(false)
      setNewEmail('')
      setNewEmailTouched(false)
      change.reset()
    },
    newEmail,
    setNewEmail,
    touchNewEmail: () => {
      setNewEmailTouched(true)
    },
    newEmailInvalid:
      (newEmailTouched && newEmail !== '' && !isEmailWellFormed(newEmail)) ||
      refusedEmail === newEmail,
    canChange: isEmailWellFormed(newEmail) && refusedEmail !== newEmail && !busy,
    changeEmail: () => {
      if (isEmailWellFormed(newEmail) && refusedEmail !== newEmail && !busy) {
        start()
        change.mutate(newEmail)
      }
    },
  }
}
