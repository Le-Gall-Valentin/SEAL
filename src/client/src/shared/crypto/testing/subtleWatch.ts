/**
 * PROTOCOL.md 6.2 : observation des tampons qui passent par crypto.subtle, pour vérifier qu'ils sont remis à zéro une
 * fois l'opération finie. Chaque tampon est gardé en vue sur sa mémoire : son contenu se relit après coup. Réservé aux
 * tests.
 */
import { vi } from 'vitest'

export interface Watch {
  /** Les tampons vus, en vues sur leur mémoire. */
  readonly seen: Uint8Array[]
  restore(): void
}

function viewOf(data: BufferSource): Uint8Array {
  return ArrayBuffer.isView(data)
    ? new Uint8Array(data.buffer, data.byteOffset, data.byteLength)
    : new Uint8Array(data)
}

/** Le texte clair que reçoit crypto.subtle.encrypt. */
export function watchEncrypt(): Watch {
  const seen: Uint8Array[] = []
  const encrypt = crypto.subtle.encrypt.bind(crypto.subtle)
  const spy = vi.spyOn(crypto.subtle, 'encrypt').mockImplementation((algorithm, key, data) => {
    seen.push(viewOf(data))
    return encrypt(algorithm, key, data)
  })
  return {
    seen,
    restore: () => {
      spy.mockRestore()
    },
  }
}

/** Le texte clair que rend crypto.subtle.decrypt. */
export function watchDecrypt(): Watch {
  const seen: Uint8Array[] = []
  const decrypt = crypto.subtle.decrypt.bind(crypto.subtle)
  const spy = vi
    .spyOn(crypto.subtle, 'decrypt')
    .mockImplementation(async (algorithm, key, data) => {
      const plaintext = await decrypt(algorithm, key, data)
      seen.push(new Uint8Array(plaintext))
      return plaintext
    })
  return {
    seen,
    restore: () => {
      spy.mockRestore()
    },
  }
}

function isEd25519(algorithm: AlgorithmIdentifier): boolean {
  return (typeof algorithm === 'string' ? algorithm : algorithm.name) === 'Ed25519'
}

/** Les données que reçoivent crypto.subtle.sign et crypto.subtle.verify en Ed25519 (HKDF de HPKE signe en HMAC). */
export function watchSigned(): Watch {
  const seen: Uint8Array[] = []
  const sign = crypto.subtle.sign.bind(crypto.subtle)
  const verify = crypto.subtle.verify.bind(crypto.subtle)
  const signSpy = vi.spyOn(crypto.subtle, 'sign').mockImplementation((algorithm, key, data) => {
    if (isEd25519(algorithm)) seen.push(viewOf(data))
    return sign(algorithm, key, data)
  })
  const verifySpy = vi
    .spyOn(crypto.subtle, 'verify')
    .mockImplementation((algorithm, key, signature, data) => {
      if (isEd25519(algorithm)) seen.push(viewOf(data))
      return verify(algorithm, key, signature, data)
    })
  return {
    seen,
    restore: () => {
      signSpy.mockRestore()
      verifySpy.mockRestore()
    },
  }
}

/** Vrai si chaque tampon vu ne contient plus que des zéros. */
export function allZero(buffers: readonly Uint8Array[]): boolean {
  return buffers.every((buffer) => buffer.every((byte) => byte === 0))
}
