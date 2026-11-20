import { describe, expect, it } from 'vitest'
import { fromBase64Url, toBase64Url } from './base64url'
import { verifyEd25519 } from './ed25519'
import { encodeProofOfPossession, encodeSubkeyBinding, KEM } from './identity'
import { generateKeyring, type Keyring, type SubkeyInfo } from './keyring'
import { bindSubkey, proveIdentity } from './publication'
import { generateRecoveryKey } from './recovery'

const USER_ID = '0192f7a1-ffff-7000-8000-00000000a11c'
const NOW = 1_794_009_600_000
const DAY_MS = 86_400_000

async function freshKeyring(): Promise<Keyring> {
  return (await generateKeyring(USER_ID, generateRecoveryKey(), NOW)).keyring
}

function firstSubkey(keyring: Keyring): SubkeyInfo {
  const [subkey] = keyring.subkeys
  if (subkey === undefined) {
    throw new TypeError('trousseau sans sous-clé')
  }
  return subkey
}

function flipped(bytes: Uint8Array): Uint8Array {
  const copy = Uint8Array.from(bytes)
  copy[copy.length - 1] = (copy[copy.length - 1] ?? 0) ^ 1
  return copy
}

describe('KEY-03 : preuve de possession', () => {
  it('KEY-03 : IK signe struct("SEAL-pop-v1", userId, ikPublic, défi), et un octet changé ne vérifie plus', async () => {
    const keyring = await freshKeyring()
    const challenge = crypto.getRandomValues(new Uint8Array(32))

    const proof = await proveIdentity(keyring, toBase64Url(challenge))

    expect(proof.ikPublic).toBe(toBase64Url(keyring.identityPublicKey))
    expect(proof.challenge).toBe(toBase64Url(challenge))
    const signed = encodeProofOfPossession({
      userId: USER_ID,
      ikPublic: keyring.identityPublicKey,
      challenge,
    })
    const signature = fromBase64Url(proof.signature)
    await expect(verifyEd25519(keyring.identityPublicKey, signed, signature)).resolves.toBe(true)
    await expect(
      verifyEd25519(keyring.identityPublicKey, flipped(signed), signature),
    ).resolves.toBe(false)
  })

  it('KEY-03 : un défi d’une autre taille que 32 octets ou en base64url non canonique est refusé', async () => {
    const keyring = await freshKeyring()

    for (const challenge of [
      toBase64Url(new Uint8Array(31)),
      toBase64Url(new Uint8Array(33)),
      `${toBase64Url(new Uint8Array(32))}=`,
    ]) {
      await expect(proveIdentity(keyring, challenge)).rejects.toThrow()
    }
  })
})

describe('KEY-05 : liaison de sous-clé', () => {
  it('KEY-05 : IK signe la liaison de la sous-clé au certificat, dates en millisecondes, 45 jours', async () => {
    const keyring = await freshKeyring()
    const subkey = firstSubkey(keyring)
    const serial = crypto.getRandomValues(new Uint8Array(16))

    const binding = await bindSubkey(keyring, toBase64Url(serial), subkey.ekId)

    expect(binding).toEqual({
      certSerial: toBase64Url(serial),
      ekId: toBase64Url(subkey.ekId),
      kem: KEM,
      ekPublic: toBase64Url(subkey.publicKey),
      createdAt: NOW,
      expiresAt: NOW + 45 * DAY_MS,
      signature: expect.any(String) as unknown,
    })
    const signed = encodeSubkeyBinding({
      userId: USER_ID,
      certSerial: serial,
      ekId: subkey.ekId,
      kem: KEM,
      ekPublic: subkey.publicKey,
      createdAt: NOW,
      expiresAt: NOW + 45 * DAY_MS,
    })
    const signature = fromBase64Url(binding.signature)
    await expect(verifyEd25519(keyring.identityPublicKey, signed, signature)).resolves.toBe(true)
    await expect(
      verifyEd25519(keyring.identityPublicKey, flipped(signed), signature),
    ).resolves.toBe(false)
  })

  it('KEY-05 : un ekId absent du trousseau ou un numéro de série de 15 octets est refusé', async () => {
    const keyring = await freshKeyring()
    const subkey = firstSubkey(keyring)

    await expect(
      bindSubkey(keyring, toBase64Url(new Uint8Array(16)), new Uint8Array(16)),
    ).rejects.toThrow(TypeError)
    await expect(bindSubkey(keyring, toBase64Url(new Uint8Array(15)), subkey.ekId)).rejects.toThrow(
      RangeError,
    )
  })
})
