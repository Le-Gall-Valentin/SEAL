/** Port de la tranche logout (ADR 0024, point 7). */
export interface ILogoutApi {
  /** AUTH-53 : ferme la session, de tout état ; le jeton CSRF est rechargé ensuite (AUTH-60). */
  logout(): Promise<void>
}
