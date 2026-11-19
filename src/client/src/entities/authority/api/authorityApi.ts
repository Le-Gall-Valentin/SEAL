import { z } from 'zod'
import { request } from '@/shared/api'
import type { IAuthorityApi } from './IAuthorityApi'

/**
 * SEC-01 : réponse stricte. Clé de 32 octets (43 caractères base64url), keyId de 8 (11 caractères), empreinte SHA-256
 * en hexadécimal majuscule ; leur cohérence est vérifiée par shared/crypto avant tout épinglage (ADR 0027, point 5).
 */
const AUTHORITY = z.strictObject({
  publicKey: z.string().regex(/^[A-Za-z0-9_-]{43}$/),
  keyId: z.string().regex(/^[A-Za-z0-9_-]{11}$/),
  fingerprint: z.string().regex(/^[0-9A-F]{64}$/),
})

/** Adaptateur HTTP du port IAuthorityApi. */
export const authorityApi: IAuthorityApi = {
  fetchAuthority: (signal) => request('/api/v1/pki/ca', AUTHORITY, signal ? { signal } : {}),
}
