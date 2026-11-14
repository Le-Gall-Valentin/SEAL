import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  isDisplayNameValid,
  isEmailWellFormed,
  localPartOf,
  usernameSuggestionFrom,
} from '@/entities/account'
import {
  canSubmitPassword,
  evaluatePassword,
  type PasswordRules,
  passwordStrength,
  usePasswordBlocklist,
} from '@/entities/password'
import { refreshSession } from '@/entities/session'
import { useRetryAfterLock } from '@/shared/lib'
import type { Registration } from '../api/IRegisterApi'
import { registerFailureOf } from './registerFailure'
import { useRegisterApi } from './registerApiContext'
import {
  type UsernameStatus,
  usernameAvailabilityKey,
  useUsernameAvailability,
} from './useUsernameAvailability'

interface Values {
  readonly email: string
  readonly username: string
  readonly displayName: string
  readonly password: string
  readonly confirm: string
}

/** Valeurs refusées par le serveur : l'erreur reste affichée tant que le champ garde cette valeur. */
interface Refused {
  readonly email?: string
  readonly displayName?: string
  readonly password?: string
  readonly username?: { readonly value: string; readonly reason: 'taken' | 'invalid' | 'reserved' }
}

const INITIAL: Values = { email: '', username: '', displayName: '', password: '', confirm: '' }

/** Statut affiché : un refus du serveur prime tant que la disponibilité ne l'a pas confirmé. */
function statusAfterRefusal(status: UsernameStatus, refused: Refused, username: string) {
  const refusal = refused.username
  if (refusal === undefined || refusal.value !== username || status.kind === 'taken') {
    return status
  }
  return refusal.reason === 'taken'
    ? ({ kind: 'taken', suggestions: [] } as const)
    : ({ kind: refusal.reason } as const)
}

/** Le serveur a refusé ce mot de passe : seule la liste de blocage échappait au client (liste non chargée). */
function rulesAfterRefusal(
  rules: PasswordRules,
  refused: Refused,
  password: string,
): PasswordRules {
  return refused.password === password && rules.blocklist !== 'unmet'
    ? { ...rules, blocklist: 'unmet' }
    : rules
}

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
  const [refused, setRefused] = useState<Refused>({})

  const username = usernameEdited
    ? values.username
    : localPartOf(values.email) === ''
      ? ''
      : usernameSuggestionFrom(values.email)
  const usernameStatus = statusAfterRefusal(useUsernameAvailability(username), refused, username)
  const rules = rulesAfterRefusal(
    evaluatePassword(values.password, { username, email: values.email }, blocklist),
    refused,
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
            void client.invalidateQueries({ queryKey: usernameAvailabilityKey(submitted.username) })
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

  const emailInvalid =
    (touched.email && values.email !== '' && !isEmailWellFormed(values.email)) ||
    refused.email === values.email
  const displayNameInvalid =
    (touched.displayName && values.displayName !== '' && !isDisplayNameValid(values.displayName)) ||
    refused.displayName === values.displayName
  const confirmMismatch =
    values.confirm !== '' &&
    values.confirm !== values.password &&
    (touched.confirm || values.confirm.length >= values.password.length)
  const valid =
    isEmailWellFormed(values.email) &&
    refused.email !== values.email &&
    (usernameStatus.kind === 'available' || usernameStatus.kind === 'unknown') &&
    isDisplayNameValid(values.displayName) &&
    refused.displayName !== values.displayName &&
    canSubmitPassword(rules) &&
    values.confirm === values.password
  const canSubmit = valid && !locked && !mutation.isPending

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
    emailInvalid,
    displayNameInvalid,
    confirmMismatch,
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
