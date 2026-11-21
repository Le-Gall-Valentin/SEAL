import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import {
  proposeUsername,
  statusAfterRefusal,
  USERNAME_AVAILABILITY_KEY,
  type UsernameRefusal,
  usernameFromEmail,
  useUsernameAvailability,
} from '@/entities/account'
import { refreshSession } from '@/entities/session'
import { ROUTES } from '@/shared/config'
import { useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import type { CompleteRegistration, PendingRegistration } from './oauthRegistration'
import { useOAuthRegistrationApi } from './oauthRegistrationApiPort'
import { oauthRegistrationFailureOf } from './oauthRegistrationFailure'
import { oauthRegistrationView } from './oauthRegistrationForm'

/** Valeurs refusées par le serveur : l'erreur reste tant que le champ garde cette valeur. */
interface Refusals {
  readonly username?: UsernameRefusal
  readonly displayName?: string
}

/**
 * AUTH-04, AUTH-05, AUTH-32 : état du formulaire d'Identifiant-OAuth. Tant que l'utilisateur ne l'a pas modifié,
 * l'identifiant suit la suggestion tirée du courriel du fournisseur ; le nom affiché part du nom proposé. Une réussite
 * relit /me, et la garde mène à l'écran de l'état du compte ; une inscription qui ne peut plus aboutir ramène à la
 * connexion (ADR 0033, point 2).
 */
export function useOAuthRegistrationForm(pending: PendingRegistration) {
  const api = useOAuthRegistrationApi()
  const client = useQueryClient()
  const navigate = useNavigate()
  const [chosenUsername, setChosenUsername] = useState<string | null>(null)
  const [displayName, setDisplayName] = useState(pending.displayName)
  const [displayNameTouched, setDisplayNameTouched] = useState(false)
  const [refused, setRefused] = useState<Refusals>({})
  const guard = useSubmitGuard()

  const base = chosenUsername ?? usernameFromEmail(pending.email)
  const baseStatus = useUsernameAvailability(base)
  const proposal =
    chosenUsername === null
      ? proposeUsername(base, baseStatus)
      : { username: base, status: baseStatus }
  const username = proposal.username
  const usernameStatus = statusAfterRefusal(proposal.status, refused.username, username)

  const mutation = useMutation({
    mutationFn: (completion: CompleteRegistration) => api.complete(completion),
    onSuccess: () => refreshSession(client),
    onError: (error, submitted) => {
      const failure = oauthRegistrationFailureOf(error)
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
        case 'displayName':
          setRefused((current) => ({ ...current, displayName: submitted.displayName }))
          return
        case 'abandoned':
          void navigate(`${ROUTES.LOGIN}?erreur=${failure.error}`, { replace: true })
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
  const failure = mutation.error === null ? null : oauthRegistrationFailureOf(mutation.error)
  const alert =
    failure !== null &&
    (failure.kind === 'rate-limited' || failure.kind === 'network' || failure.kind === 'unexpected')
      ? failure
      : null

  const view = oauthRegistrationView({
    username,
    status: usernameStatus,
    displayName,
    displayNameTouched,
    refusedDisplayName: refused.displayName,
  })
  const canSubmit = view.valid && !locked && !mutation.isPending

  return {
    username,
    usernameStatus,
    usernameFromEmail: chosenUsername === null,
    setUsername: setChosenUsername,
    displayName,
    setDisplayName,
    touchDisplayName: () => {
      setDisplayNameTouched(true)
    },
    displayNameInvalid: view.displayNameInvalid,
    alert,
    canSubmit,
    submit: () => {
      if (canSubmit && guard.enter()) {
        mutation.mutate({ username, displayName })
      }
    },
  }
}
