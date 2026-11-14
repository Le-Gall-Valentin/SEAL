/** ADR 0022, point 1 : ce que l'écran Nouveau-Mot-de-passe affiche d'un lien vivant. */
export interface ResetLink {
  readonly username: string
  readonly email: string
  readonly expiresAt: Date
}

/** Port de la tranche password-reset (ADR 0024, point 7) ; routes réservées aux anonymes (ADR 0022). */
export interface IPasswordResetApi {
  /** AUTH-70 : toujours 204, que l'adresse ait un compte ou non. */
  request(email: string): Promise<void>
  /** ADR 0022 : 422 CODE_EXPIRED pour un lien inconnu, expiré ou déjà servi. */
  inspect(token: string): Promise<ResetLink>
  /** AUTH-70 : remplace le mot de passe et ferme toutes les sessions ; n'en ouvre aucune. */
  confirm(token: string, password: string): Promise<void>
}
