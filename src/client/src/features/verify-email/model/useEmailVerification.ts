import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { type Account, refreshSession } from '@/entities/session'
import { ApiError, useRetryAfterLock } from '@/shared/lib'
import { CODE_LENGTH, verifyFailureOf } from './code'
import { useSecondsLeft } from './useSecondsLeft'
import { verificationView } from './verificationView'
import { useVerifyEmailApi } from './verifyEmailApiPort'

const EMPTY_CODE: readonly string[] = Array.from({ length: CODE_LENGTH }, () => '')

interface VerificationProps {
  readonly email: string
  readonly verification: Account['verification']
}

/**
 * AUTH-14, AUTH-17 : vérification du code, renvoi et changement du courriel en attente. Une seule action à la fois
 * affiche son erreur. Le renvoi et le changement partagent leur limite : un 429 de l'un suspend les deux, jamais la
 * vérification du code en cours. Chaque réussite relit /me : le compte à rebours et les renvois restants en viennent,
 * et la garde mène à l'accueil une fois le compte actif.
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

  const sendError = resend.error ?? change.error
  const verifyLocked = useRetryAfterLock(verify.error, verify.reset)
  const sendLocked = useRetryAfterLock(sendError, () => {
    resend.reset()
    change.reset()
  })
  const secondsLeft = useSecondsLeft(verification?.codeExpiresAt ?? null)
  const view = verificationView({
    code: cells.join(''),
    secondsLeft,
    codeAlive: verification?.codeExpiresAt !== null && verification?.codeExpiresAt !== undefined,
    resendsRemaining: verification?.resendsRemaining ?? 0,
    verifyError: verify.error,
    sendError,
    verifyLocked,
    sendLocked,
    pending: verify.isPending || resend.isPending || change.isPending,
    newEmail,
    newEmailTouched,
    refusedEmail,
  })

  /** Une action efface l'erreur et la confirmation de la précédente. */
  const start = () => {
    verify.reset()
    resend.reset()
    change.reset()
    setSentTo(null)
  }

  return {
    cells,
    setCells,
    secondsLeft: secondsLeft ?? 0,
    alert: view.alert,
    codeLocked: verifyLocked,
    canVerify: view.canVerify,
    verify: () => {
      if (view.canVerify) {
        start()
        verify.mutate(cells.join(''))
      }
    },
    resendsRemaining: verification?.resendsRemaining ?? 0,
    canResend: view.canResend,
    resend: () => {
      if (view.canResend) {
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
    newEmailInvalid: view.newEmailInvalid,
    canChange: view.canChange,
    changeEmail: () => {
      if (view.canChange) {
        start()
        change.mutate(newEmail)
      }
    },
  }
}
