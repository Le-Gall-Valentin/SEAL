import { useMutation } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
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
import { failureOf, useClock, withLock } from '@/shared/lib'
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
 * verrou des clés, puis la zone du compte complète la publication (onRestored).
 */
export function useKeyRestore(owner: KeyOwner, onRestored: () => void): KeyRestore {
  const api = useKeysApi()
  const store = useKeyStore()
  const clock = useClock()
  const [groups, setGroups] = useState(EMPTY_GROUPS)
  const recoveryKey = useMemo(() => recoveryKeyOf(groups), [groups])
  const restore = useMutation({
    mutationFn: (key: RecoveryKey) =>
      withLock(KEYS_LOCK, () => restoreKeys({ api, store, owner, clock }, key)),
    onSuccess: onRestored,
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
        restore.mutate(recoveryKey)
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
