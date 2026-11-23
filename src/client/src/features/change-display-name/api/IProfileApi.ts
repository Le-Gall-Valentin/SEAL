import type { Account } from '@/entities/session'

/** Port de PATCH /api/v1/account/profile (ACC-03, ADR 0034). */
export interface IProfileApi {
  /** Nouveau nom affiché ; rend le compte courant, nom normalisé NFC. */
  changeDisplayName(displayName: string): Promise<Account>
}
