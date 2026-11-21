import type { OAuthProviderId } from '@/entities/oauth-provider'

/** AUTH-32 : l'inscription OAuth en attente, telle que l'écran Identifiant-OAuth l'affiche. */
export interface PendingRegistration {
  readonly provider: OAuthProviderId
  readonly email: string
  /** Nom proposé par le fournisseur, conforme à ACC-03, ou vide. */
  readonly displayName: string
}

/** AUTH-32 : les deux champs choisis à la fin de l'inscription OAuth. */
export interface CompleteRegistration {
  readonly username: string
  readonly displayName: string
}

/** Port de la tranche oauth-registration (ADR 0033). */
export interface IOAuthRegistrationApi {
  /** L'inscription en attente de la session, ou null s'il n'y en a pas (404, ADR 0033, point 2). */
  pending(signal?: AbortSignal): Promise<PendingRegistration | null>
  /** Crée et lie le compte, puis ouvre la session ; le jeton CSRF est rechargé ensuite (AUTH-60). */
  complete(registration: CompleteRegistration): Promise<void>
}
