/**
 * CDC 10.3, étape 1 : saisie et noms comparés en NFKD, sans diacritiques, en minuscules, sans espaces ni `@` initial.
 * Partagé par le sélecteur d'amis (FRD-20) et la recherche dans les listes (ADR 0038 point 6, CNV-10).
 */
export function normalizeSearch(text: string): string {
  return text
    .normalize('NFKD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/\s+/gu, '')
    .replace(/^@/u, '')
}
