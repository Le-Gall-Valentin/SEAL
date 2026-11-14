import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import {
  canSubmitPassword,
  evaluatePassword,
  type PasswordRules,
  passwordStrength,
  usePasswordBlocklist,
} from '@/entities/password'
import { ROUTES } from '@/shared/config'
import { useRetryAfterLock } from '@/shared/lib'
import { minutesLeft, resetFailureOf } from './resetLink'
import { usePasswordResetApi } from './passwordResetApiPort'

/** État de navigation de la connexion après une réinitialisation réussie (ADR 0024, point 9). */
export interface PasswordResetDone {
  readonly passwordReset: true
}

/**
 * AUTH-70, ADR 0022 : lecture du lien, puis choix du nouveau mot de passe, avec les règles d'AUTH-11 pour le compte du
 * lien. Une réussite mène à la connexion, sans session ouverte ; un lien mort affiche « Ce lien n'est plus valable. ».
 */
export function useNewPassword(token: string | null) {
  const api = usePasswordResetApi()
  const navigate = useNavigate()
  const blocklist = usePasswordBlocklist()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [confirmTouched, setConfirmTouched] = useState(false)
  const [refusedPassword, setRefusedPassword] = useState<string | null>(null)
  const [linkDead, setLinkDead] = useState(false)

  // Le jeton n'entre pas dans la clé : il ne vit que dans la fermeture, le temps de l'écran.
  const link = useQuery({
    queryKey: ['password-reset', 'inspect'],
    queryFn: async () => {
      if (token === null) {
        throw new Error('jeton absent')
      }
      const link = await api.inspect(token)
      // « Ce lien expire dans N minutes », compté à la lecture du lien.
      return { ...link, minutesLeft: minutesLeft(link.expiresAt, Date.now()) }
    },
    enabled: token !== null,
    staleTime: Infinity,
    gcTime: 0,
  })
  const mutation = useMutation({
    mutationFn: (newPassword: string) =>
      token === null ? Promise.reject(new Error('jeton absent')) : api.confirm(token, newPassword),
    onSuccess: () => {
      const state: PasswordResetDone = { passwordReset: true }
      void navigate(ROUTES.LOGIN, { replace: true, state })
    },
    onError: (error, submitted) => {
      const failure = resetFailureOf(error)
      if (failure.kind === 'expired-link') {
        setLinkDead(true)
      } else if (failure.kind === 'password') {
        setRefusedPassword(submitted)
      }
    },
  })
  const locked = useRetryAfterLock(mutation.error, mutation.reset)

  const account = link.data
  const evaluated = evaluatePassword(
    password,
    { username: account?.username ?? '', email: account?.email ?? '' },
    blocklist,
  )
  // Le serveur a refusé ce mot de passe : seule la liste de blocage échappait au client.
  const rules: PasswordRules =
    refusedPassword === password && evaluated.blocklist !== 'unmet'
      ? { ...evaluated, blocklist: 'unmet' }
      : evaluated
  const failure = mutation.error === null ? null : resetFailureOf(mutation.error)
  const confirmMismatch =
    confirm !== '' && confirm !== password && (confirmTouched || confirm.length >= password.length)
  const canSubmit =
    account !== undefined &&
    canSubmitPassword(rules) &&
    confirm === password &&
    !locked &&
    !mutation.isPending

  return {
    /** « loading » tant que le lien n'est pas lu ; « dead » pour un lien absent, inconnu, expiré ou servi. */
    state:
      token === null ||
      linkDead ||
      (link.isError && resetFailureOf(link.error).kind === 'expired-link')
        ? ('dead' as const)
        : link.isError
          ? ('error' as const)
          : account === undefined
            ? ('loading' as const)
            : ('ready' as const),
    linkFailure: link.isError ? resetFailureOf(link.error) : null,
    account,
    password,
    setPassword,
    confirm,
    setConfirm,
    touchConfirm: () => {
      setConfirmTouched(true)
    },
    rules,
    strength: passwordStrength(password, rules),
    confirmMismatch,
    alert:
      failure !== null &&
      (failure.kind === 'rate-limited' ||
        failure.kind === 'network' ||
        failure.kind === 'unexpected')
        ? failure
        : null,
    canSubmit,
    submit: () => {
      if (canSubmit) {
        mutation.mutate(password)
      }
    },
  }
}
