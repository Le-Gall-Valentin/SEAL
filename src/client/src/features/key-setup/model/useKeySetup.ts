import { useMutation, useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ForeignIdentityError,
  KEYS_LOCK,
  type KeyOwner,
  prepareReset,
  resetIdentity,
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
import { ApiError, downloadText, failureOf, useClock, useHeldLock } from '@/shared/lib'
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
  /** ADR 0029, point 12 : échec de la sauvegarde, réseau ou inattendu ; null sinon. */
  readonly failure: 'network' | 'unexpected' | null
}

const GROUP_LENGTH = 4

/**
 * KEY-34, ADR 0036 : le dialogue sert à la réinitialisation de l'identité. Sur 403 REAUTH_REQUIRED, l'écran ouvre
 * « Confirmez votre identité », puis appelle retry, qui renvoie la même identité.
 */
export interface KeyResetOptions {
  readonly onReauthRequired: (retry: () => void) => void
}

/**
 * KEY-02, KEY-30, ADR 0029 (points 2 et 3) : Dialogue-Cle-Recuperation. La clé de récupération est tirée à l'ouverture
 * et montrée une seule fois ; deux groupes tirés au hasard la confirment ; « Terminer » génère alors IK, EK et la VDK,
 * une fois, et lance setupKeys (coffre, trousseau, publication). Rien n'est envoyé avant. Le verrou des clés est tenu
 * tant que le dialogue est ouvert : un autre onglet attend au lieu d'en ouvrir un second. Avec `reset`, « Terminer »
 * prépare une nouvelle identité et la fait remplacer l'ancienne (KEY-34, ADR 0036).
 */
export function useKeySetup(
  owner: KeyOwner,
  onDone: () => void,
  reset?: KeyResetOptions,
): KeySetup {
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

  // Un autre onglet a pu créer le trousseau pendant que ce dialogue attendait le verrou. Lu une seule fois, à
  // l'obtention du verrou : une relecture (retour du focus, reconnexion) trouverait celui que « Terminer » enregistre.
  const existing = useQuery({
    queryKey: ['keyring', 'setup', owner.userId],
    queryFn: async () => (await store.loadKeyring(owner.userId)) !== undefined,
    enabled: lock.held,
    gcTime: 0,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  })
  // Une sauvegarde à la fois, même pour deux « Terminer » avant le rendu suivant.
  const running = useRef(false)

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

  // Trousseau créé ailleurs avant toute sauvegarde : le dialogue se ferme sans montrer de clé. Appareil illisible : la
  // zone du compte reprend la main et affiche l'erreur, au lieu d'une page blanche qui garderait le verrou.
  useEffect(() => {
    if ((existing.data === true && !running.current) || existing.isError) {
      leave()
    }
  }, [existing.data, existing.isError, leave])

  // « Terminer », ou la relance après la ré-authentification (KEY-34) ; le verrou running garde une seule sauvegarde.
  function start(): void {
    if (!running.current) {
      running.current = true
      setup.mutate()
    }
  }

  const setup = useMutation({
    mutationFn: async () => {
      const context = { api, store, owner, clock }
      if (reset === undefined) {
        pending.current ??= await generateKeyring(owner.userId, recoveryKey, clock.now())
        return setupKeys(context, pending.current)
      }
      // KEY-34 : préparée une fois, l'identité est gardée pour la relance qui suit la ré-authentification.
      pending.current ??= await prepareReset(context, recoveryKey)
      return resetIdentity(context, pending.current)
    },
    onSuccess: leave,
    onError: (error) => {
      // Certificat d'une autre IK, coffre rejoué : la zone du compte décide de l'écran (ADR 0029, point 5).
      if (error instanceof ForeignIdentityError || error instanceof StaleVaultError) {
        leave()
      } else if (reset !== undefined && isReauthRequired(error)) {
        reset.onReauthRequired(start)
      }
    },
    onSettled: () => {
      running.current = false
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
      if (confirmed) {
        start()
      }
    },
    saving: setup.isPending,
    failure: failureFrom(setup.error),
  }
}

/** AUTH-55 : le mode sudo est fermé ; ce n'est pas un échec à afficher, la ré-authentification s'ouvre. */
function isReauthRequired(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403 && error.code === 'REAUTH_REQUIRED'
}

function failureFrom(error: Error | null): KeySetup['failure'] {
  if (error === null || isReauthRequired(error)) {
    return null
  }
  return failureOf(error).kind === 'network' ? 'network' : 'unexpected'
}
