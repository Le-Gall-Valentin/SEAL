/** AUTH-20 : identifiant ou courriel, et mot de passe. */
export interface Credentials {
  readonly login: string
  readonly password: string
}

/** Port de la tranche login (ADR 0024, point 7). */
export interface ILoginApi {
  /** Ouvre la session ; le jeton CSRF est rechargé ensuite (AUTH-60). */
  login(credentials: Credentials): Promise<void>
}
