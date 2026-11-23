import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { EMAIL_MAX_LENGTH, isEmailWellFormed } from '@/entities/account'
import { type Account, replaceSessionAccount } from '@/entities/session'
import {
  EMPTY_CODE,
  isCodeComplete,
  isFormFailure,
  useRetryAfterLock,
  useSubmitGuard,
} from '@/shared/lib'
import { emailChangeFailureOf } from './emailChangeFailure'
import { useEmailChangeApi } from './emailChangeApiPort'

export type EmailChangeStep = 'address' | 'code'

interface EmailChangeOptions {
  readonly account: Account
  /** 403 REAUTH_REQUIRED : l'écran ouvre la ré-authentification, puis appelle retry (ADR 0034, ADR 0035). */
  readonly onReauthRequired: (retry: () => void) => void
  readonly onChanged: () => void
}

/** L'adresse actuelle ne se redemande pas : le serveur la refuserait (400). */
function isSameAddress(email: string, current: string): boolean {
  return email.toLowerCase() === current.toLowerCase()
}

/**
 * ACC-02 : étape 1, la nouvelle adresse (mode sudo exigé par le serveur) ; étape 2, le code à 6 chiffres reçu à cette
 * adresse. Une réussite remplace le compte du cache (ADR 0035) et ramène le dialogue à l'étape 1, vide. Un 429 sur les
 * envois (3 par heure) bloque l'envoi et le renvoi jusqu'à la fin de Retry-After. Fermer à l'étape 2 abandonne le code
 * (ADR 0035).
 */
export function useEmailChange({ account, onReauthRequired, onChanged }: EmailChangeOptions) {
  const client = useQueryClient()
  const api = useEmailChangeApi()
  const [step, setStep] = useState<EmailChangeStep>('address')
  const [email, setEmail] = useState('')
  const [emailTouched, setEmailTouched] = useState(false)
  const [refusedEmail, setRefusedEmail] = useState<string | null>(null)
  const [target, setTarget] = useState('')
  const [resentTo, setResentTo] = useState<string | null>(null)
  const [cells, setCells] = useState<readonly string[]>(EMPTY_CODE)
  /** Change chaque fois que les cases se vident pour une saisie : l'écran y place le focus (case 1). */
  const [codeFocus, setCodeFocus] = useState(0)
  const sendGuard = useSubmitGuard()
  const confirmGuard = useSubmitGuard()

  const send = useMutation({
    mutationFn: (address: string) => api.request(address),
    onSuccess: (_result, address) => {
      setResentTo(step === 'code' ? address : null)
      setTarget(address)
      setCells(EMPTY_CODE)
      confirm.reset()
      setStep('code')
      setCodeFocus((count) => count + 1)
    },
    onError: (error, address) => {
      const kind = emailChangeFailureOf(error).kind
      if (kind === 'reauth-required') {
        onReauthRequired(() => {
          sendGuard.run(() => send.mutateAsync(address))
        })
      } else if (kind === 'invalid-email') {
        setRefusedEmail(address)
      }
    },
  })
  const confirm = useMutation({
    mutationFn: (code: string) => api.confirm(code),
    onSuccess: (updated) => {
      replaceSessionAccount(client, updated)
      reset()
      onChanged()
    },
    onError: (error) => {
      setResentTo(null)
      if (emailChangeFailureOf(error).kind === 'invalid-code') {
        setCells(EMPTY_CODE)
        setCodeFocus((count) => count + 1)
      }
    },
  })
  const sendLocked = useRetryAfterLock(send.error, send.reset)

  /** Retour à l'étape 1, sans rien de saisi ; un 429 en cours reste tenu. */
  function reset() {
    setStep('address')
    setEmail('')
    setEmailTouched(false)
    setRefusedEmail(null)
    setTarget('')
    setResentTo(null)
    setCells(EMPTY_CODE)
    confirm.reset()
    if (!sendLocked) {
      send.reset()
    }
  }

  const sendFailure = send.error === null ? null : emailChangeFailureOf(send.error)
  const confirmFailure = confirm.error === null ? null : emailChangeFailureOf(confirm.error)
  const pending = send.isPending || confirm.isPending
  const address = email.trim()
  const refused = refusedEmail !== null && address === refusedEmail
  const addressReady =
    address.length <= EMAIL_MAX_LENGTH &&
    isEmailWellFormed(address) &&
    !isSameAddress(address, account.email) &&
    !refused

  return {
    step,
    email,
    setEmail,
    touchEmail: () => {
      setEmailTouched(true)
    },
    emailInvalid:
      refused ||
      (emailTouched &&
        address !== '' &&
        (!isEmailWellFormed(address) || isSameAddress(address, account.email))),
    target,
    /** Adresse d'un renvoi réussi : « Code envoyé à … ». */
    resentTo,
    cells,
    setCells,
    codeFocus,
    /** Refus sans place dans un champ : 429, réseau, erreur inattendue. */
    sendFailure: sendFailure !== null && isFormFailure(sendFailure) ? sendFailure : null,
    codeFailure:
      confirmFailure?.kind === 'invalid-code' || confirmFailure?.kind === 'expired'
        ? confirmFailure.kind
        : null,
    confirmFailure:
      confirmFailure !== null && isFormFailure(confirmFailure) ? confirmFailure : null,
    sendLocked,
    pending,
    canSend: !sendLocked && !pending && addressReady,
    canResend: !sendLocked && !pending,
    canConfirm: !pending && isCodeComplete(cells),
    sendAddress: () => {
      if (!sendLocked && !pending && addressReady) {
        sendGuard.run(() => send.mutateAsync(address))
      }
    },
    resend: () => {
      if (!sendLocked && !pending) {
        sendGuard.run(() => send.mutateAsync(target))
      }
    },
    confirmCode: () => {
      if (!pending && isCodeComplete(cells)) {
        confirmGuard.run(() => confirm.mutateAsync(cells.join('')))
      }
    },
    clear: reset,
  }
}
