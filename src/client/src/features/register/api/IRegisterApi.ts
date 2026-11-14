/** AUTH-10 : champs de l'inscription, envoyés tels que saisis ; le serveur normalise. */
export interface Registration {
  readonly email: string
  readonly username: string
  readonly displayName: string
  readonly password: string
}

/** AUTH-05 : disponibilité d'un identifiant et trois suggestions libres au plus. */
export interface UsernameAvailability {
  readonly available: boolean
  readonly suggestions: readonly string[]
}

/** Port de la tranche register (ADR 0024, point 7). */
export interface IRegisterApi {
  /** Crée le compte en attente et ouvre la session restreinte ; le jeton CSRF est rechargé ensuite (AUTH-60). */
  register(registration: Registration): Promise<void>
  usernameAvailability(username: string, signal?: AbortSignal): Promise<UsernameAvailability>
}
