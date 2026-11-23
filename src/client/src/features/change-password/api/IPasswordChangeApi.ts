/** Demande de POST /api/v1/account/password (ACC-01, ADR 0034). */
export interface PasswordChange {
  readonly currentPassword: string
  readonly newPassword: string
  /** « Déconnecter mes autres sessions », cochée par défaut. */
  readonly signOutOtherSessions: boolean
}

/** Port du changement de mot de passe. */
export interface IPasswordChangeApi {
  /** La session courante reste ouverte, sous un nouvel identifiant et avec un nouveau jeton CSRF. */
  changePassword(change: PasswordChange): Promise<void>
}
