import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import {
  canSubmitPassword,
  evaluatePassword,
  PASSWORD_INPUT_MAX_LENGTH,
  passwordStrength,
  rulesAfterRefusal,
  usePasswordBlocklist,
} from '@/entities/password'
import type { Account } from '@/entities/session'
import { isFormFailure, useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import { passwordChangeFailureOf } from './passwordChangeFailure'
import { usePasswordChangeApi } from './passwordChangeApiPort'

/**
 * ACC-01 : mot de passe actuel, nouveau mot de passe (règles d'AUTH-11 pour ce compte) et « Déconnecter mes autres
 * sessions », cochée par défaut. Un mot de passe actuel refusé vide son champ ; un nouveau refusé par le serveur
 * marque la liste de blocage ; un 429 bloque le formulaire jusqu'à la fin de Retry-After.
 */
export function usePasswordChange(account: Account, onChanged: () => void) {
  const api = usePasswordChangeApi()
  const blocklist = usePasswordBlocklist()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [signOut, setSignOut] = useState(true)
  const [refusedNext, setRefusedNext] = useState<string | null>(null)
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: (change: {
      currentPassword: string
      newPassword: string
      signOutOtherSessions: boolean
    }) => api.changePassword(change),
    onSuccess: () => {
      setCurrent('')
      setNext('')
      setSignOut(true)
      setRefusedNext(null)
      onChanged()
    },
    onError: (error, change) => {
      const failure = passwordChangeFailureOf(error)
      if (failure.kind === 'invalid-current' || failure.kind === 'rate-limited') {
        setCurrent('')
      } else if (failure.kind === 'weak') {
        setRefusedNext(change.newPassword)
      }
    },
  })
  const { error, reset } = mutation
  const locked = useRetryAfterLock(error, reset)
  const evaluated = evaluatePassword(
    next,
    { username: account.username, email: account.email },
    blocklist,
  )
  const rules = rulesAfterRefusal(evaluated, refusedNext, next)
  const failure = error === null ? null : passwordChangeFailureOf(error)
  const ready =
    current !== '' && current.length <= PASSWORD_INPUT_MAX_LENGTH && canSubmitPassword(rules)
  return {
    current,
    setCurrent,
    next,
    setNext,
    signOut,
    setSignOut,
    rules,
    strength: passwordStrength(next, rules),
    currentRefused: failure?.kind === 'invalid-current',
    formFailure: failure !== null && isFormFailure(failure) ? failure : null,
    locked,
    pending: mutation.isPending,
    canSubmit: !locked && !mutation.isPending && ready,
    submit: () => {
      if (!locked && !mutation.isPending && ready) {
        guard.run(() =>
          mutation.mutateAsync({
            currentPassword: current,
            newPassword: next,
            signOutOtherSessions: signOut,
          }),
        )
      }
    },
    /** Fermeture du dialogue : aucun mot de passe ne reste en mémoire pour la prochaine ouverture. */
    clear: () => {
      setCurrent('')
      setNext('')
      setSignOut(true)
      setRefusedNext(null)
      if (!locked) {
        reset()
      }
    },
  }
}
