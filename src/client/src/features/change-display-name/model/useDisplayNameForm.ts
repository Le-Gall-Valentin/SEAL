import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { isDisplayNameShownInvalid, isDisplayNameValid } from '@/entities/account'
import { type Account, replaceSessionAccount } from '@/entities/session'
import { failureOf, isFormFailure, useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import { useProfileApi } from './profileApiPort'

/**
 * ACC-03 : carte Profil. « Enregistrer » n'est actif que pour un nom changé et conforme à la règle d'AUTH-10 ; une
 * réussite remplace le compte du cache (ADR 0035) et l'annonce ; un 400 garde le nom refusé signalé.
 */
export function useDisplayNameForm(account: Account) {
  const client = useQueryClient()
  const api = useProfileApi()
  const [value, setValue] = useState(account.displayName)
  const [touched, setTouched] = useState(false)
  const [refused, setRefused] = useState<string | undefined>(undefined)
  const [saved, setSaved] = useState(false)
  const guard = useSubmitGuard()
  const mutation = useMutation({
    mutationFn: (displayName: string) => api.changeDisplayName(displayName),
    onSuccess: (updated) => {
      replaceSessionAccount(client, updated)
      setValue(updated.displayName)
      setTouched(false)
      setSaved(true)
    },
    onError: (error, displayName) => {
      const failure = failureOf(error)
      if (failure.kind === 'refused' && failure.code === 'VALIDATION_FAILED') {
        setRefused(displayName)
      }
    },
  })
  const { error, reset } = mutation
  const locked = useRetryAfterLock(error, reset)
  const ready =
    value.normalize('NFC') !== account.displayName && isDisplayNameValid(value) && refused !== value
  const failure = error === null ? null : failureOf(error)
  return {
    value,
    setValue: (next: string) => {
      setValue(next)
      setSaved(false)
    },
    touch: () => {
      setTouched(true)
    },
    invalid: isDisplayNameShownInvalid({ value, touched, refused }),
    formFailure: failure !== null && isFormFailure(failure) ? failure : null,
    saved,
    locked,
    canSubmit: !locked && !mutation.isPending && ready,
    submit: () => {
      if (!locked && !mutation.isPending && ready) {
        setSaved(false)
        guard.run(() => mutation.mutateAsync(value))
      }
    },
  }
}
