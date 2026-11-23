/** Port de POST /api/v1/auth/reauth (AUTH-55, ADR 0034). */
export interface IReauthApi {
  /** Ouvre le mode sudo par le mot de passe ; la session reçoit un nouvel identifiant et un nouveau jeton CSRF. */
  reauthenticate(password: string): Promise<void>
}
