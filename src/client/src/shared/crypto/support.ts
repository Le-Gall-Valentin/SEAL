/**
 * KEY-01 (CDC, section 4) : le client teste Ed25519 et X25519 dans WebCrypto au démarrage, et refuse de fonctionner
 * sinon. Chaque algorithme est éprouvé de bout en bout, pas seulement détecté : une signature doit se vérifier, et les
 * deux côtés d'un échange X25519 doivent dériver le même secret.
 */
import { generateX25519 } from './webcrypto'
import { equalBytes } from './bytes'

const PROBE = new TextEncoder().encode('SEAL KEY-01')

export async function checkCryptoSupport(): Promise<boolean> {
  try {
    return (await ed25519Works()) && (await x25519Works())
  } catch {
    return false
  }
}

async function ed25519Works(): Promise<boolean> {
  const pair = await crypto.subtle.generateKey({ name: 'Ed25519' }, false, ['sign', 'verify'])
  const signature = await crypto.subtle.sign({ name: 'Ed25519' }, pair.privateKey, PROBE)
  return crypto.subtle.verify({ name: 'Ed25519' }, pair.publicKey, signature, PROBE)
}

async function x25519Works(): Promise<boolean> {
  const alice = await generateX25519(false, ['deriveBits'])
  const bob = await generateX25519(false, ['deriveBits'])
  const fromAlice = await crypto.subtle.deriveBits(
    { name: 'X25519', public: bob.publicKey },
    alice.privateKey,
    256,
  )
  const fromBob = await crypto.subtle.deriveBits(
    { name: 'X25519', public: alice.publicKey },
    bob.privateKey,
    256,
  )
  // Un secret partagé X25519 fait 32 octets : une dérivation plus courte ne prouve rien.
  return (
    fromAlice.byteLength === 32 && equalBytes(new Uint8Array(fromAlice), new Uint8Array(fromBob))
  )
}
