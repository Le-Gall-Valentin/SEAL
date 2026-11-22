import type { Account } from '@/entities/session'

/** Port du changement de courriel (ACC-02, ADR 0034). */
export interface IEmailChangeApi {
  /** Mode sudo exigé ; même réponse que l'adresse soit prise ou non (AUTH-15). */
  request(email: string): Promise<void>
  /** Le code reçu à la nouvelle adresse ; rend le compte courant, nouvelle adresse comprise. */
  confirm(code: string): Promise<Account>
}
