import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  evaluatePassword,
  passwordStrength,
  rulesAfterRefusal,
  usePasswordBlocklist,
} from '@/entities/password'
import { useUsernameChoice } from '@/entities/account'
import { refreshSession } from '@/entities/session'
import { isFormFailure, useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import type { Registration } from '../api/IRegisterApi'
import { type RegisterRefusals, type RegisterValues, registerView } from './registerForm'
import { registerFailureOf } from './registerFailure'
import { useRegisterApi } from './registerApiPort'

const INITIAL: RegisterValues = { email: '', displayName: '', password: '', confirm: '' }

/**
 * AUTH-03 à AUTH-05, AUTH-10, AUTH-11 : état du formulaire d'inscription. Tant que l'utilisateur ne l'a pas modifié,
 * l'identifiant suit la suggestion tirée du courriel (AUTH-04). Une réussite relit /me ; la garde mène alors à la
 * vérification du courriel.
 */
export function useRegisterForm() {
  const api = useRegisterApi()
  const client = useQueryClient()
  const blocklist = usePasswordBlocklist()
  const [values, setValues] = useState<RegisterValues>(INITIAL)
  const [touched, setTouched] = useState({ email: false, displayName: false, confirm: false })
  const [refused, setRefused] = useState<RegisterRefusals>({})
  const guard = useSubmitGuard()

  const choice = useUsernameChoice(values.email)
  const username = choice.username
  const usernameStatus = choice.status
  const rules = rulesAfterRefusal(
    evaluatePassword(values.password, { username, email: values.email }, blocklist),
    refused.password ?? null,
    values.password,
  )

  const mutation = useMutation({
    mutationFn: (registration: Registration) => api.register(registration),
    onSuccess: () => refreshSession(client),
    onError: (error, submitted) => {
      const failure = registerFailureOf(error)
      switch (failure.kind) {
        case 'username':
          choice.refuse(submitted.username, failure.reason)
          return
        case 'email':
          setRefused((current) => ({ ...current, email: submitted.email }))
          return
        case 'displayName':
          setRefused((current) => ({ ...current, displayName: submitted.displayName }))
          return
        case 'password':
          setRefused((current) => ({ ...current, password: submitted.password }))
          return
        case 'rate-limited':
        case 'network':
        case 'unexpected':
          // Affichés en alerte au-dessus du formulaire.
          return
      }
    },
    onSettled: () => {
      guard.release()
    },
  })
  const locked = useRetryAfterLock(mutation.error, mutation.reset)
  const failure = mutation.error === null ? null : registerFailureOf(mutation.error)
  const alert = failure !== null && isFormFailure(failure) ? failure : null

  const view = registerView({ values, username, status: usernameStatus, rules, touched, refused })
  const canSubmit = view.valid && !locked && !mutation.isPending

  const set = (field: keyof RegisterValues) => (value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
  }
  const touch = (field: keyof typeof touched) => () => {
    setTouched((current) => ({ ...current, [field]: true }))
  }

  return {
    values: { ...values, username },
    setEmail: set('email'),
    setUsername: choice.choose,
    setDisplayName: set('displayName'),
    setPassword: set('password'),
    setConfirm: set('confirm'),
    touchEmail: touch('email'),
    touchDisplayName: touch('displayName'),
    touchConfirm: touch('confirm'),
    usernameStatus,
    usernameFromEmail: choice.fromEmail,
    rules,
    strength: passwordStrength(values.password, rules),
    emailInvalid: view.emailInvalid,
    displayNameInvalid: view.displayNameInvalid,
    confirmMismatch: view.confirmMismatch,
    alert,
    locked,
    canSubmit,
    submit: () => {
      if (canSubmit && guard.enter()) {
        mutation.mutate({
          email: values.email,
          username,
          displayName: values.displayName,
          password: values.password,
        })
      }
    },
  }
}
