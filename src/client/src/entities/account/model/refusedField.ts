/** Champ du compte refusé par VALIDATION_FAILED (ADR 0010 : le champ, sans sa valeur), le premier dans cet ordre. */
export type AccountField = 'email' | 'displayName' | 'username'

const ORDER: readonly AccountField[] = ['email', 'displayName', 'username']

export function refusedAccountField(fields: readonly string[]): AccountField | null {
  return ORDER.find((field) => fields.includes(field)) ?? null
}
