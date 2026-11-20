/**
 * KEY-22, CDC 7.1 : encapsulation de la CEK par HPKE (mode base, DHKEM X25519 HKDF-SHA256, HKDF-SHA256,
 * AES-256-GCM), commune aux tests sous Node et dans Chromium. Réservé aux tests.
 */
import { Aes256Gcm, CipherSuite, DhkemX25519HkdfSha256, HkdfSha256 } from '@hpke/core'
import { describe, expect, it } from 'vitest'
import { HpkeOpenError, openCek, sealCek } from '../hpke'
import { generateKeyring, type Keyring, keyringSecrets } from '../keyring'
import { generateRecoveryKey } from '../recovery'

const USER_ID = '0192f7a1-ffff-7000-8000-00000000a11c'
const NOW = 1_794_009_600_000
const INFO = new TextEncoder().encode('info du destinataire')
const AAD = new TextEncoder().encode('en-tête du message')

async function recipient(): Promise<{
  readonly keyring: Keyring
  readonly key: { readonly privateKey: CryptoKey; readonly publicKey: Uint8Array }
}> {
  const { keyring } = await generateKeyring(USER_ID, generateRecoveryKey(), NOW)
  const privateKey = keyringSecrets(keyring).subkeyKeys[0]
  const subkey = keyring.subkeys[0]
  if (privateKey === undefined || subkey === undefined) throw new Error('une sous-clé attendue')
  return { keyring, key: { privateKey, publicKey: subkey.publicKey } }
}

function flipped(bytes: Uint8Array, index: number): Uint8Array {
  const copy = Uint8Array.from(bytes)
  copy[index] = (copy[index] ?? 0) ^ 0x01
  return copy
}

export function describeHpke(engine: string): void {
  describe(`KEY-22 : encapsulation de la CEK par HPKE (${engine})`, () => {
    it('KEY-22 : la clé privée non extractable d’une EK du trousseau ouvre ce que scelle sa clé publique', async () => {
      const { key } = await recipient()
      const cek = crypto.getRandomValues(new Uint8Array(32))

      const { enc, wrappedCek } = await sealCek(key.publicKey, INFO, AAD, cek)
      const opened = await openCek(key, enc, wrappedCek, INFO, AAD)

      expect(key.privateKey.extractable).toBe(false)
      expect(opened).toEqual(cek)
      expect(enc).toHaveLength(32)
      expect(wrappedCek).toHaveLength(48)
    })

    it('CDC 7.3 : une autre info, une autre AAD ou une autre clé ne l’ouvrent pas', async () => {
      const { key } = await recipient()
      const other = await recipient()
      const cek = crypto.getRandomValues(new Uint8Array(32))
      const { enc, wrappedCek } = await sealCek(key.publicKey, INFO, AAD, cek)

      // Chaque ouverture part au moment où elle est attendue : une promesse rejetée avant d'être écoutée serait
      // signalée comme une erreur non gérée.
      for (const attempt of [
        () => openCek(key, enc, wrappedCek, new TextEncoder().encode('autre info'), AAD),
        () => openCek(key, enc, wrappedCek, INFO, new TextEncoder().encode('autre en-tête')),
        () => openCek(other.key, enc, wrappedCek, INFO, AAD),
      ]) {
        await expect(attempt()).rejects.toBeInstanceOf(HpkeOpenError)
      }
    })

    it('QA-04 : chaque octet de enc ou de la CEK encapsulée, altéré, fait échouer l’ouverture', async () => {
      const { key } = await recipient()
      const cek = crypto.getRandomValues(new Uint8Array(32))
      const { enc, wrappedCek } = await sealCek(key.publicKey, INFO, AAD, cek)

      for (let index = 0; index < enc.length; index += 1) {
        await expect(
          openCek(key, flipped(enc, index), wrappedCek, INFO, AAD),
          `enc ${String(index)}`,
        ).rejects.toBeInstanceOf(HpkeOpenError)
      }
      for (let index = 0; index < wrappedCek.length; index += 1) {
        await expect(
          openCek(key, enc, flipped(wrappedCek, index), INFO, AAD),
          `wrappedCek ${String(index)}`,
        ).rejects.toBeInstanceOf(HpkeOpenError)
      }
    })

    it('CDC 7.1 : une CEK d’une autre taille que 32 octets n’est pas scellée', async () => {
      const { key } = await recipient()

      await expect(sealCek(key.publicKey, INFO, AAD, new Uint8Array(31))).rejects.toBeInstanceOf(
        RangeError,
      )
    })

    it('CDC 7.1 : une CEK encapsulée d’une autre taille que 32 octets n’est pas ouverte', async () => {
      const { key } = await recipient()
      const suite = new CipherSuite({
        kem: new DhkemX25519HkdfSha256(),
        kdf: new HkdfSha256(),
        aead: new Aes256Gcm(),
      })
      const { enc, ct } = await suite.seal(
        {
          recipientPublicKey: await suite.kem.importKey(
            'raw',
            Uint8Array.from(key.publicKey).buffer,
            true,
          ),
          info: INFO,
        },
        new Uint8Array(16),
        AAD,
      )

      await expect(
        openCek(key, new Uint8Array(enc), new Uint8Array(ct), INFO, AAD),
      ).rejects.toBeInstanceOf(HpkeOpenError)
    })
  })
}
