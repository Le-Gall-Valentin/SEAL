/** Compte courant de GET /api/v1/auth/me (CDC, section 14.1), en types du client. */
export interface Account {
  readonly id: string
  readonly username: string
  readonly displayName: string
  readonly email: string
  readonly status: 'PENDING_EMAIL' | 'ACTIVE'
  /** AUTH-14 : renseignée pour un compte en attente ; codeExpiresAt est nul quand aucun code n'est vivant. */
  readonly verification: {
    readonly codeExpiresAt: Date | null
    readonly resendsRemaining: number
  } | null
}

/** Port de la tranche session (ADR 0024, point 7) : les hooks ne connaissent que lui. */
export interface ISessionApi {
  /** Compte de la session, ou null pour un anonyme. */
  me(signal?: AbortSignal): Promise<Account | null>
  /** AUTH-60 : jeton CSRF rechargé au démarrage. */
  reloadCsrf(): Promise<void>
}
