import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  evaluatePassword,
  passwordStrength,
  rulesAfterRefusal,
  usePasswordBlocklist,
} from '@/entities/password'
import { refreshSession } from '@/entities/session'
import { useRetryAfterLock } from '@/shared/lib'
import type { Registration } from '../api/IRegisterApi'
import {
  proposeUsername,
  type RegisterRefusals,
  type RegisterValues,
  registerView,
  statusAfterRefusal,
  usernameFromEmail,
} from './registerForm'
import { registerFailureOf } from './registerFailure'
import { useRegisterApi } from './registerApiPort'
import { USERNAME_AVAILABILITY_KEY, useUsernameAvailability } from './useUsernameAvailability'

interface Values extends RegisterValues {
  readonly username: string
}

const INITIAL: Values = { email: '', username: '', displayName: '', password: '', confirm: '' }

/**
 * AUTH-03 à AUTH-05, AUTH-10, AUTH-11 : état du formulaire d'inscription. Tant que l'utilisateur ne l'a pas modifié,
 * l'identifiant suit la suggestion tirée du courriel (AUTH-04). Une réussite relit /me ; la garde mène alors à la
 * vérification du courriel.
 */
export function useRegisterForm() {
  const api = useRegisterApi()
  const client = useQueryClient()
  const blocklist = usePasswordBlocklist()
  const [values, setValues] = useState<Values>(INITIAL)
  const [usernameEdited, setUsernameEdited] = useState(false)
  const [touched, setTouched] = useState({ email: false, displayName: false, confirm: false })
  const [refused, setRefused] = useState<RegisterRefusals>({})

  // AUTH-04 : tant qu'il n'est pas modifié, l'identifiant suit le courriel, et la première suggestion libre remplace
  // une base prise ou réservée (étape 5).
  const base = usernameEdited ? values.username : usernameFromEmail(values.email)
  const baseStatus = useUsernameAvailability(base)
  const proposal = usernameEdited
    ? { username: base, status: baseStatus }
    : proposeUsername(base, baseStatus)
  const username = proposal.username
  const usernameStatus = statusAfterRefusal(proposal.status, refused.username, username)
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
          setRefused((current) => ({
            ...current,
            username: { value: submitted.username, reason: failure.reason },
          }))
          if (failure.reason === 'taken') {
            // Les suggestions lues ont pu vieillir : la base et l'identifiant refusé sont relus.
            void client.invalidateQueries({ queryKey: USERNAME_AVAILABILITY_KEY })
          }
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
  })
  const locked = useRetryAfterLock(mutation.error, mutation.reset)
  const failure = mutation.error === null ? null : registerFailureOf(mutation.error)
  const alert =
    failure !== null &&
    (failure.kind === 'rate-limited' || failure.kind === 'network' || failure.kind === 'unexpected')
      ? failure
      : null

  const view = registerView({ values, username, status: usernameStatus, rules, touched, refused })
  const canSubmit = view.valid && !locked && !mutation.isPending

  const set = (field: keyof Values) => (value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
  }
  const touch = (field: keyof typeof touched) => () => {
    setTouched((current) => ({ ...current, [field]: true }))
  }
  const chooseUsername = (value: string) => {
    setUsernameEdited(true)
    setValues((current) => ({ ...current, username: value }))
  }

  return {
    values: { ...values, username },
    setEmail: set('email'),
    setUsername: chooseUsername,
    setDisplayName: set('displayName'),
    setPassword: set('password'),
    setConfirm: set('confirm'),
    touchEmail: touch('email'),
    touchDisplayName: touch('displayName'),
    touchConfirm: touch('confirm'),
    usernameStatus,
    usernameFromEmail: !usernameEdited,
    rules,
    strength: passwordStrength(values.password, rules),
    emailInvalid: view.emailInvalid,
    displayNameInvalid: view.displayNameInvalid,
    confirmMismatch: view.confirmMismatch,
    alert,
    locked,
    canSubmit,
    submit: () => {
      if (canSubmit) {
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
