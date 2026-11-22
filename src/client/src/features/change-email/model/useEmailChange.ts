import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { EMAIL_MAX_LENGTH, isEmailWellFormed } from '@/entities/account'
import { type Account, replaceSessionAccount } from '@/entities/session'
import { CODE_LENGTH, isFormFailure, useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import { emailChangeFailureOf } from './emailChangeFailure'
import { useEmailChangeApi } from './emailChangeApiPort'

const EMPTY_CODE: readonly string[] = Array.from({ length: CODE_LENGTH }, () => '')

export type EmailChangeStep = 'address' | 'code'

interface EmailChangeOptions {
  readonly account: Account
  /** 403 REAUTH_REQUIRED : l'écran ouvre la ré-authentification, puis appelle retry (ADR 0034, ADR 0035). */
  readonly onReauthRequired: (retry: () => void) => void
  readonly onChanged: () => void
}

/** L'adresse actuelle ne se redemande pas : le serveur la refuserait (400). */
function isSameAddress(email: string, current: string): boolean {
  return email.trim().toLowerCase() === current.toLowerCase()
}

/**
 * ACC-02 : étape 1, la nouvelle adresse (mode sudo exigé par le serveur) ; étape 2, le code à 6 chiffres reçu à cette
 * adresse. Une réussite remplace le compte du cache (ADR 0035). Un 429 sur les envois (3 par heure) bloque l'envoi et le
 * renvoi jusqu'à la fin de Retry-After.
 */
export function useEmailChange({ account, onReauthRequired, onChanged }: EmailChangeOptions) {
  const client = useQueryClient()
  const api = useEmailChangeApi()
  const [step, setStep] = useState<EmailChangeStep>('address')
  const [email, setEmail] = useState('')
  const [emailTouched, setEmailTouched] = useState(false)
  const [target, setTarget] = useState('')
  const [cells, setCells] = useState<readonly string[]>(EMPTY_CODE)
  const sendGuard = useSubmitGuard()
  const confirmGuard = useSubmitGuard()

  const send = useMutation({
    mutationFn: (address: string) => api.request(address),
    onSuccess: (_result, address) => {
      setTarget(address)
      setCells(EMPTY_CODE)
      confirm.reset()
      setStep('code')
    },
    onError: (error, address) => {
      if (emailChangeFailureOf(error).kind === 'reauth-required') {
        onReauthRequired(() => {
          sendGuard.run(() => send.mutateAsync(address))
        })
      }
    },
  })
  const confirm = useMutation({
    mutationFn: (code: string) => api.confirm(code),
    onSuccess: (updated) => {
      replaceSessionAccount(client, updated)
      onChanged()
    },
    onError: (error) => {
      if (emailChangeFailureOf(error).kind === 'invalid-code') {
        setCells(EMPTY_CODE)
      }
    },
  })
  const sendLocked = useRetryAfterLock(send.error, send.reset)
  const sendFailure = send.error === null ? null : emailChangeFailureOf(send.error)
  const confirmFailure = confirm.error === null ? null : emailChangeFailureOf(confirm.error)
  const pending = send.isPending || confirm.isPending
  const address = email.trim()
  const addressReady =
    address.length <= EMAIL_MAX_LENGTH &&
    isEmailWellFormed(address) &&
    !isSameAddress(address, account.email)
  const code = cells.join('')
  const codeReady = /^[0-9]{6}$/.test(code)

  return {
    step,
    email,
    setEmail,
    touchEmail: () => {
      setEmailTouched(true)
    },
    emailInvalid:
      (emailTouched && address !== '' && !isEmailWellFormed(address)) ||
      sendFailure?.kind === 'invalid-email',
    target,
    cells,
    setCells,
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
    canConfirm: !pending && codeReady,
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
      if (!pending && codeReady) {
        confirmGuard.run(() => confirm.mutateAsync(code))
      }
    },
    /** Fermeture : retour à l'étape 1, sans rien de saisi ; un 429 en cours reste tenu. */
    clear: () => {
      setStep('address')
      setEmail('')
      setEmailTouched(false)
      setTarget('')
      setCells(EMPTY_CODE)
      confirm.reset()
      if (!sendLocked) {
        send.reset()
      }
    },
  }
}
