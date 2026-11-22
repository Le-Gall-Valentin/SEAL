import { useMutation } from '@tanstack/react-query'
import { useMemo, useRef, useState } from 'react'
import {
  InvalidVaultError,
  KEYS_LOCK,
  type KeyOwner,
  restoreKeys,
  useKeysApi,
  useKeyStore,
  WrongRecoveryKeyError,
} from '@/entities/keyring'
import type { RecoveryKey } from '@/shared/crypto'
import { failureOf, useClock, useSubmitGuard, withLock } from '@/shared/lib'
import { EMPTY_GROUPS, enterGroups, recoveryKeyOf } from './recoveryKeyText'

/** Refus d'une restauration (ADR 0029, point 5) ; réseau et erreur inattendue reprennent les textes communs. */
export type RestoreFailure =
  | { readonly kind: 'wrong-key' }
  | { readonly kind: 'invalid' }
  | { readonly kind: 'network' }
  | { readonly kind: 'unexpected' }

export interface KeyRestore {
  readonly groups: readonly string[]
  /** Saisie dans la case `index` ; rend la case qui doit prendre le focus. */
  readonly enter: (index: number, input: string) => number
  readonly canSubmit: boolean
  readonly submit: () => void
  readonly pending: boolean
  readonly failure: RestoreFailure | null
}

/**
 * KEY-33 : Restauration-Appareil. La clé saisie ne quitte pas l'appareil : restoreKeys ouvre le coffre ici, sous le
 * verrou des clés, puis la zone du compte complète la publication (onRestored). KEY-31, KEY-40 : la clé n'entre pas
 * dans le cache des mutations (ni en variables, ni gardée après le démontage) ; elle ne vit que dans l'écran. Une seule
 * restauration à la fois (ADR 0033, point 6) : sans verrou d'envoi, le verrou des clés rangerait un second envoi
 * derrière le premier, et la restauration serait rejouée une fois finie.
 */
export function useKeyRestore(owner: KeyOwner, onRestored: () => void): KeyRestore {
  const api = useKeysApi()
  const store = useKeyStore()
  const clock = useClock()
  const [groups, setGroups] = useState(EMPTY_GROUPS)
  const recoveryKey = useMemo(() => recoveryKeyOf(groups), [groups])
  const submitted = useRef<RecoveryKey | null>(null)
  const guard = useSubmitGuard()
  const restore = useMutation({
    mutationFn: () => {
      const key = submitted.current
      if (key === null) {
        return Promise.reject(new TypeError('KEY-33 : aucune clé envoyée'))
      }
      return withLock(KEYS_LOCK, () => restoreKeys({ api, store, owner, clock }, key))
    },
    onSuccess: onRestored,
    onSettled: () => {
      submitted.current = null
    },
    gcTime: 0,
  })

  return {
    groups,
    enter: (index, input) => {
      const entered = enterGroups(groups, index, input)
      setGroups(entered.groups)
      restore.reset()
      return entered.focus
    },
    canSubmit: recoveryKey !== null && !restore.isPending,
    submit: () => {
      if (recoveryKey !== null && !restore.isPending) {
        guard.run(() => {
          submitted.current = recoveryKey
          return restore.mutateAsync()
        })
      }
    },
    pending: restore.isPending,
    failure: restore.error === null ? null : failureFrom(restore.error),
  }
}

function failureFrom(error: Error): RestoreFailure {
  if (error instanceof WrongRecoveryKeyError) {
    return { kind: 'wrong-key' }
  }
  if (error instanceof InvalidVaultError) {
    return { kind: 'invalid' }
  }
  return failureOf(error).kind === 'network' ? { kind: 'network' } : { kind: 'unexpected' }
}
