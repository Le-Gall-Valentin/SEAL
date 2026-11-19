import { useMutation, useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ForeignIdentityError,
  KEYS_LOCK,
  type KeyOwner,
  setupKeys,
  StaleVaultError,
  useKeysApi,
  useKeyStore,
} from '@/entities/keyring'
import { useMessages } from '@/shared/i18n'
import {
  generateKeyring,
  generateRecoveryKey,
  type NewKeyring,
  pickRecoveryGroups,
} from '@/shared/crypto'
import { downloadText, useClock, useHeldLock } from '@/shared/lib'
import { keySetupMessages } from '../i18n'
import { recoveryKeyFile } from './recoveryKeyFile'

/** Une case de l'étape 2 : le premier ou le second groupe tiré. */
export type Slot = 0 | 1

export interface KeySetup {
  /** Le verrou des clés est tenu et aucun trousseau n'existe : la clé peut s'afficher (ADR 0029, point 3). */
  readonly ready: boolean
  readonly step: 1 | 2
  readonly groups: readonly string[]
  /** Index (0 à 7) des deux groupes à ressaisir, dans l'ordre. */
  readonly picked: readonly [number, number]
  readonly typed: readonly [string, string]
  readonly type: (slot: Slot, value: string) => void
  /** Vrai dès que la case contient 4 caractères qui ne sont pas le groupe attendu. */
  readonly mismatch: (slot: Slot) => boolean
  readonly confirmed: boolean
  readonly showKey: () => void
  readonly verify: () => void
  readonly download: () => void
  readonly finish: () => void
  readonly saving: boolean
  readonly failed: boolean
}

const GROUP_LENGTH = 4

/**
 * KEY-02, KEY-30, ADR 0029 (points 2 et 3) : Dialogue-Cle-Recuperation. La clé de récupération est tirée à l'ouverture
 * et montrée une seule fois ; deux groupes tirés au hasard la confirment ; « Terminer » génère alors IK, EK et la VDK,
 * une fois, et lance setupKeys (coffre, trousseau, publication). Rien n'est envoyé avant. Le verrou des clés est tenu
 * tant que le dialogue est ouvert : un autre onglet attend au lieu d'en ouvrir un second.
 */
export function useKeySetup(owner: KeyOwner, onDone: () => void): KeySetup {
  const t = useMessages(keySetupMessages)
  const api = useKeysApi()
  const store = useKeyStore()
  const clock = useClock()
  const lock = useHeldLock(KEYS_LOCK)
  const [recoveryKey] = useState(generateRecoveryKey)
  const [picked] = useState(pickRecoveryGroups)
  const [step, setStep] = useState<1 | 2>(1)
  const [typed, setTyped] = useState<readonly [string, string]>(['', ''])
  const pending = useRef<NewKeyring | null>(null)

  // Un autre onglet a pu créer le trousseau pendant que ce dialogue attendait le verrou.
  const existing = useQuery({
    queryKey: ['keyring', 'setup', owner.userId],
    queryFn: async () => (await store.loadKeyring(owner.userId)) !== undefined,
    enabled: lock.held,
    gcTime: 0,
  })

  // Le dialogue ne se quitte qu'une fois, quel que soit le nombre de rendus qui suivent.
  const left = useRef(false)
  const { release } = lock
  const leave = useCallback((): void => {
    if (!left.current) {
      left.current = true
      release()
      onDone()
    }
  }, [release, onDone])

  useEffect(() => {
    if (existing.data === true) {
      leave()
    }
  }, [existing.data, leave])

  const setup = useMutation({
    mutationFn: async () => {
      pending.current ??= await generateKeyring(owner.userId, recoveryKey, clock.now())
      return setupKeys({ api, store, owner, clock }, pending.current)
    },
    onSuccess: leave,
    onError: (error) => {
      // Certificat d'une autre IK, coffre rejoué : la zone du compte décide de l'écran (ADR 0029, point 5).
      if (error instanceof ForeignIdentityError || error instanceof StaleVaultError) {
        leave()
      }
    },
  })

  const groups = recoveryKey.groups()
  const matches = (slot: Slot): boolean => recoveryKey.matchesGroup(picked[slot], typed[slot])
  const confirmed = matches(0) && matches(1)

  return {
    ready: lock.held && existing.data === false,
    step,
    groups,
    picked,
    typed,
    type: (slot, value) => {
      setTyped((current) => (slot === 0 ? [value, current[1]] : [current[0], value]))
    },
    mismatch: (slot) => typed[slot].replace(/[\s-]/g, '').length >= GROUP_LENGTH && !matches(slot),
    confirmed,
    showKey: () => {
      setStep(1)
    },
    verify: () => {
      setStep(2)
    },
    download: () => {
      const file = recoveryKeyFile(owner.username, groups, {
        title: t('fileTitle'),
        account: t('fileAccount', { username: owner.username }),
        warning: t('showText'),
      })
      downloadText(file.name, file.text)
    },
    finish: () => {
      if (confirmed && !setup.isPending) {
        setup.mutate()
      }
    },
    saving: setup.isPending,
    failed: setup.isError,
  }
}
