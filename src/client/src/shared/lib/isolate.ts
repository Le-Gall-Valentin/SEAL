/** Isolat de direction de l'algorithme bidirectionnel (UAX #9) : FSI au début, PDI à la fin. */
const FIRST_STRONG_ISOLATE = '\u2068'
const POP_DIRECTIONAL_ISOLATE = '\u2069'

/**
 * ADR 0017, ADR 0038 (point 9) : isole un texte libre interpolé dans une phrase affichée, pour qu'un nom écrit de droite
 * à gauche ne réordonne pas la phrase qui l'entoure.
 */
export function isolate(text: string): string {
  return `${FIRST_STRONG_ISOLATE}${text}${POP_DIRECTIONAL_ISOLATE}`
}
