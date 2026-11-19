/**
 * Vecteurs du protocole pour les tests du trousseau, dans Chromium : le coffre de vault.json porte l'IK du certificat
 * d'identity.json, que l'Autorité des vecteurs signe (PROTOCOL.md 4.5 et 5.4). Réservé aux tests.
 */
import { commands } from 'vitest/browser'
import { z } from 'zod'
import {
  checkAuthority,
  type Keyring,
  type KeyStore,
  openKeyStore,
  openVault,
  parseRecoveryKey,
  vaultFromWire,
} from '@/shared/crypto'
import type { CertificateView, VaultView } from '../../api/IKeysApi'

const VAULT = z.strictObject({
  vaultVersion: z.number(),
  salt: z.string(),
  nonceK: z.string(),
  wrappedVdk: z.string(),
  nonceB: z.string(),
  blob: z.string(),
})

const VAULT_VECTORS = z.object({
  userId: z.string(),
  recoveryKey: z.object({ groups: z.array(z.string()) }),
  keyring: z.object({ eks: z.array(z.object({ createdAt: z.number() })) }),
  payload: VAULT,
  nextVersion: z.object({ payload: VAULT }),
})

const IDENTITY_VECTORS = z.object({
  authority: z.object({ publicKey: z.string(), keyId: z.string(), fingerprint: z.string() }),
  certificates: z.array(
    z.object({
      fields: z.object({
        serial: z.string(),
        issuerKeyId: z.string(),
        subjectId: z.string(),
        username: z.string(),
        ikPublic: z.string(),
        notBefore: z.string(),
        notAfter: z.string(),
        usage: z.number(),
      }),
      signature: z.string(),
    }),
  ),
})

// Chemins relatifs à la racine du client, comme les résout readFile.
const vault = VAULT_VECTORS.parse(
  JSON.parse(await commands.readFile('../protocol/vectors/vault.json')),
)
const identity = IDENTITY_VECTORS.parse(
  JSON.parse(await commands.readFile('../protocol/vectors/identity.json')),
)
const certificate = identity.certificates[0]
if (certificate === undefined) {
  throw new Error('identity.json : un certificat attendu')
}

/** Le certificat d'identity.json, tel que l'adaptateur le rend : dates en millisecondes. */
export const VECTOR_CERTIFICATE: CertificateView = {
  ...certificate.fields,
  notBefore: Number(certificate.fields.notBefore),
  notAfter: Number(certificate.fields.notAfter),
  signature: certificate.signature,
}

/** Titulaire du trousseau et du certificat des vecteurs. */
export const VECTOR_OWNER = { userId: vault.userId, username: VECTOR_CERTIFICATE.username }

/** Coffre des vecteurs, version 1 puis version 2 (une seconde sous-clé). */
export const VECTOR_VAULT: VaultView = vault.payload
export const VECTOR_NEXT_VAULT: VaultView = vault.nextVersion.payload

/** Création de la sous-clé du trousseau des vecteurs, en millisecondes. */
export const VECTOR_SUBKEY_CREATED_AT = vault.keyring.eks[0]?.createdAt ?? Number.NaN

export function vectorRecoveryKeyText(): string {
  return vault.recoveryKey.groups.join(' ')
}

/** Le trousseau des vecteurs, rouvert par sa clé de récupération. */
export async function vectorKeyring(): Promise<Keyring> {
  return openVault(
    vault.userId,
    vaultFromWire(VECTOR_VAULT),
    parseRecoveryKey(vectorRecoveryKeyText()),
  )
}

let opened = 0

/** Trousseau IndexedDB neuf, que nul autre test n'ouvre, où l'Autorité des vecteurs est épinglée (KEY-11). */
export async function pinnedStore(): Promise<KeyStore> {
  opened += 1
  const store = await openKeyStore(`seal-keyring-test-${String(Date.now())}-${String(opened)}`)
  await checkAuthority(store, identity.authority)
  return store
}
