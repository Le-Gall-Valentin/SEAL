/** ACC-05 : langues de l'interface (préférences, Parametres-Telephone-Preferences). */
export type Language = 'fr' | 'en'

/** Textes d'une langue, par clé. */
export type Texts = Readonly<Record<string, string>>

/**
 * Textes d'une tranche (ADR 0017) : le français est obligatoire, l'anglais facultatif tant qu'ACC-05 n'est pas livré ;
 * un texte anglais absent ou vide retombe sur le français.
 */
export interface Messages<Fr extends Texts> {
  readonly fr: Fr
  readonly en?: { readonly [Key in keyof Fr]?: string }
}

/** Paramètres d'un texte, interpolés à la place de {nom}. */
export type MessageParams = Readonly<Record<string, string | number>>

type Placeholders<Text extends string> = Text extends `${string}{${infer Name}}${infer Rest}`
  ? Name | Placeholders<Rest>
  : never

/** Paramètres de t() pour un texte (Q6) : aucun sans marque {nom}, sinon un objet qui les nomme toutes. */
export type ParamsOf<Text extends string> = [Placeholders<Text>] extends [never]
  ? []
  : [params: Readonly<Record<Placeholders<Text>, string | number>>]

/** Déclare les textes d'une tranche ; leurs clés et leurs marques typent la fonction t() que renvoie useMessages. */
export function defineMessages<const Fr extends Texts>(messages: Messages<Fr>): Messages<Fr> {
  return messages
}

/** Vrai si la clé, venue de l'extérieur (code d'erreur de l'API…), a un texte propre, jamais un héritage d'Object. */
export function hasMessage<Fr extends Texts>(
  messages: Messages<Fr>,
  key: string,
): key is Extract<keyof Fr, string> {
  return Object.hasOwn(messages.fr, key)
}
