/** Port de la tranche verify-email (ADR 0024, point 7) ; routes réservées à la session restreinte (AUTH-16). */
export interface IVerifyEmailApi {
  /** AUTH-14 : rend la session complète ; le jeton CSRF est rechargé ensuite (AUTH-60). */
  verify(code: string): Promise<void>
  /** AUTH-14 : 3 renvois par heure. */
  resend(): Promise<void>
  /** AUTH-17 : remplace le courriel en attente et envoie un nouveau code, dans la limite des renvois. */
  changeEmail(email: string): Promise<void>
}
