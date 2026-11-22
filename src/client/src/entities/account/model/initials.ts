/**
 * Initiales de l'avatar (Parametres-* : « Votre avatar reprend les initiales de votre nom affiché. », ADR 0035) : la
 * première lettre des deux premiers mots du nom affiché, en majuscules. Un nom d'un seul mot donne une lettre. Les
 * lettres sont des graphèmes entiers : une lettre accentuée décomposée ou un caractère hors du plan de base reste
 * entier.
 */
export function initialsOf(displayName: string): string {
  const segmenter = new Intl.Segmenter('fr', { granularity: 'grapheme' })
  return displayName
    .trim()
    .split(/\s+/u)
    .filter((word) => word !== '')
    .slice(0, 2)
    .map((word) => segmenter.segment(word)[Symbol.iterator]().next().value?.segment ?? '')
    .join('')
    .toLocaleUpperCase('fr')
}
