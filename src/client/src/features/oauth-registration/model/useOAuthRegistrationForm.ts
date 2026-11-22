import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useUsernameChoice } from '@/entities/account'
import { loginPathWithError } from '@/entities/oauth-provider'
import { refreshSession } from '@/entities/session'
import { isFormFailure, useRetryAfterLock, useSubmitGuard } from '@/shared/lib'
import type { CompleteRegistration, PendingRegistration } from './oauthRegistration'
import { useOAuthRegistrationApi } from './oauthRegistrationApiPort'
import { oauthRegistrationFailureOf } from './oauthRegistrationFailure'
import { oauthRegistrationView } from './oauthRegistrationForm'

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
  const [displayName, setDisplayName] = useState(pending.displayName)
  const [displayNameTouched, setDisplayNameTouched] = useState(false)
  // Nom refusé par le serveur : l'erreur reste tant que le champ garde cette valeur.
  const [refusedDisplayName, setRefusedDisplayName] = useState<string | undefined>(undefined)
  const guard = useSubmitGuard()

  const choice = useUsernameChoice(pending.email)
  const username = choice.username

  const mutation = useMutation({
    mutationFn: (completion: CompleteRegistration) => api.complete(completion),
    onSuccess: () => refreshSession(client),
    onError: (error, submitted) => {
      const failure = oauthRegistrationFailureOf(error)
      switch (failure.kind) {
        case 'username':
          choice.refuse(submitted.username, failure.reason)
          return
        case 'displayName':
          setRefusedDisplayName(submitted.displayName)
          return
        case 'abandoned':
          // Navigation portée par l'envoi (submit) : elle n'a lieu que si l'écran est encore affiché.
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
  const failure = mutation.error === null ? null : oauthRegistrationFailureOf(mutation.error)
  const alert = failure !== null && isFormFailure(failure) ? failure : null

  const view = oauthRegistrationView({
    username,
    status: choice.status,
    displayName,
    displayNameTouched,
    refusedDisplayName,
  })
  const canSubmit = view.valid && !locked && !mutation.isPending

  return {
    username,
    usernameStatus: choice.status,
    usernameFromEmail: choice.fromEmail,
    setUsername: choice.choose,
    displayName,
    setDisplayName,
    touchDisplayName: () => {
      setDisplayNameTouched(true)
    },
    displayNameInvalid: view.displayNameInvalid,
    alert,
    canSubmit,
    submit: () => {
      if (canSubmit) {
        guard.run(() =>
          mutation.mutateAsync(
            { username, displayName },
            {
              onError: (error) => {
                const failure = oauthRegistrationFailureOf(error)
                if (failure.kind === 'abandoned') {
                  void navigate(loginPathWithError(failure.error), { replace: true })
                }
              },
            },
          ),
        )
      }
    },
  }
}
