/** ACC-05 : langues de l'interface (préférences, Parametres-Telephone-Preferences). */
export type Language = 'fr' | 'en'

/**
 * Textes d'une tranche (ADR 0017) : le français est obligatoire, l'anglais facultatif tant qu'ACC-05 n'est pas livré ;
 * un texte anglais absent retombe sur le français.
 */
export interface Messages<Key extends string> {
  readonly fr: Readonly<Record<Key, string>>
  readonly en?: Readonly<Partial<Record<Key, string>>>
}

/** Paramètres d'un texte, interpolés à la place de {nom}. */
export type MessageParams = Readonly<Record<string, string | number>>

/** Déclare les textes d'une tranche ; leurs clés typent la fonction t() que renvoie useMessages. */
export function defineMessages<const Key extends string>(messages: Messages<Key>): Messages<Key> {
  return messages
}
