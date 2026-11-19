import { describe, expect, it } from 'vitest'
import { generateKeyring, keyringSecrets, openVault } from './keyring'

const USER_ID = '0192f7a1-ffff-7000-8000-00000000a11c'

// KEY-40 : le client tourne dans Chromium, dont WebCrypto exporte et relit lui-même le PKCS#8 et le JWK du trousseau.
describe('KEY-40 : trousseau dans Chromium', () => {
  it('KEY-33, KEY-40 : un trousseau généré se rouvre par RK, signe, et aucune clé n’est extractable', async () => {
    const generated = await generateKeyring(USER_ID, Date.now())
    const opened = await openVault(USER_ID, generated.vault, generated.recoveryKey)
    const message = new TextEncoder().encode('SEAL')
    const signature = await crypto.subtle.sign(
      'Ed25519',
      keyringSecrets(opened).identityKey,
      message,
    )
    const publicKey = await crypto.subtle.importKey(
      'raw',
      generated.keyring.identityPublicKey,
      'Ed25519',
      false,
      ['verify'],
    )

    await expect(crypto.subtle.verify('Ed25519', publicKey, signature, message)).resolves.toBe(true)
    expect(opened.subkeys[0]?.publicKey).toEqual(generated.keyring.subkeys[0]?.publicKey)
    for (const keyring of [opened, generated.keyring]) {
      const secrets = keyringSecrets(keyring)
      for (const key of [secrets.identityKey, ...secrets.subkeyKeys, secrets.vdk]) {
        expect(key.extractable).toBe(false)
      }
    }
  })
})
