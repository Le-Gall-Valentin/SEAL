/**
 * Clé de récupération RK (KEY-30, KEY-31) : 20 octets aléatoires, montrés en base32 Crockford, 32 caractères en 8 groupes
 * de 4. Ses octets restent dans ce module : l'objet ne se sérialise pas, ne s'affiche pas en texte et n'a aucune
 * propriété énumérable. Seuls ses groupes sortent, pour l'affichage unique et la ressaisie.
 */
import { decodeCrockford, encodeCrockford } from './crockford'

const BYTES = 20
const GROUPS = 8
const GROUP_LENGTH = 4

/** Octets de chaque clé, hors de l'objet : ni la console ni un clonage ne les montrent. */
const SECRETS = new WeakMap<RecoveryKey, Uint8Array>()

export class RecoveryKeyFormatError extends Error {
  override readonly name = 'RecoveryKeyFormatError'
}

export class RecoveryKey {
  private constructor(bytes: Uint8Array) {
    SECRETS.set(this, Uint8Array.from(bytes))
  }

  /** Construction réservée à ce module : par generateRecoveryKey ou parseRecoveryKey. */
  static of(bytes: Uint8Array): RecoveryKey {
    return new RecoveryKey(bytes)
  }

  /** KEY-30 : 8 groupes de 4 caractères. */
  groups(): readonly string[] {
    const text = encodeCrockford(recoveryKeyBytes(this))
    return Array.from({ length: GROUPS }, (_, index) =>
      text.slice(index * GROUP_LENGTH, (index + 1) * GROUP_LENGTH),
    )
  }

  /** KEY-31 : la clé ne part jamais dans un corps JSON. */
  toJSON(): never {
    throw new TypeError('KEY-31 : la clé de récupération ne se sérialise pas')
  }

  toString(): string {
    return '[RecoveryKey]'
  }
}

/** KEY-30 : 20 octets de crypto.getRandomValues. */
export function generateRecoveryKey(): RecoveryKey {
  return RecoveryKey.of(crypto.getRandomValues(new Uint8Array(BYTES)))
}

/** KEY-33 : la clé ressaisie, exactement 32 caractères utiles, sinon RecoveryKeyFormatError. */
export function parseRecoveryKey(input: string): RecoveryKey {
  let bytes: Uint8Array
  try {
    bytes = decodeCrockford(input)
  } catch {
    throw new RecoveryKeyFormatError('clé de récupération illisible')
  }
  if (bytes.length !== BYTES) {
    throw new RecoveryKeyFormatError('une clé de récupération fait 32 caractères')
  }
  return RecoveryKey.of(bytes)
}

/** Copie des octets, pour le coffre (6.4) ; interne à shared/crypto. */
export function recoveryKeyBytes(key: RecoveryKey): Uint8Array<ArrayBuffer> {
  const bytes = SECRETS.get(key)
  if (bytes === undefined) {
    throw new TypeError('clé de récupération inconnue')
  }
  return Uint8Array.from(bytes)
}

/** KEY-30 : deux index distincts de groupes à ressaisir, tirés uniformément, dans l'ordre croissant. */
export function pickRecoveryGroups(): readonly [number, number] {
  const first = uniformBelow(GROUPS)
  let second = uniformBelow(GROUPS - 1)
  if (second >= first) {
    second += 1
  }
  return first < second ? [first, second] : [second, first]
}

/** Entier uniforme dans [0, bound), par rejet des octets qui biaiseraient le modulo. */
function uniformBelow(bound: number): number {
  const limit = 256 - (256 % bound)
  for (;;) {
    const byte = new DataView(crypto.getRandomValues(new Uint8Array(1)).buffer).getUint8(0)
    if (byte < limit) {
      return byte % bound
    }
  }
}
