/** AUTH-05 : disponibilité d'un identifiant et trois suggestions libres au plus. */
export interface UsernameAvailability {
  readonly available: boolean
  readonly suggestions: readonly string[]
}

/** Port de la disponibilité des identifiants, partagé par les deux inscriptions (ADR 0033). */
export interface IUsernameAvailabilityApi {
  usernameAvailability(username: string, signal?: AbortSignal): Promise<UsernameAvailability>
}
