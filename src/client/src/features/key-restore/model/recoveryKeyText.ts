import { parseRecoveryKey, type RecoveryKey } from '@/shared/crypto'

/** KEY-30 : 8 groupes de 4 caractères. */
const GROUP_COUNT = 8
const GROUP_LENGTH = 4

export const EMPTY_GROUPS: readonly string[] = Array.from({ length: GROUP_COUNT }, () => '')

/**
 * KEY-33 : saisie dans la case `index`. Séparateurs retirés, le texte remplit cette case puis les suivantes par 4
 * caractères (une clé collée d'un bloc se répartit), sans dépasser la huitième. Un seul caractère tapé dans une case
 * pleine est refusé : il écraserait la case suivante. Le focus va à la case qui suit un groupe complet. La casse est
 * gardée telle que saisie : la mettre en majuscules ici lirait ı comme I (ADR 0027, point 15).
 */
export function enterGroups(
  groups: readonly string[],
  index: number,
  input: string,
): { readonly groups: readonly string[]; readonly focus: number } {
  const next = [...groups]
  const compact = input.replace(/[\s-]/g, '')
  if (compact === '') {
    next[index] = ''
    return { groups: next, focus: index }
  }
  const before = (groups[index] ?? '').replace(/[\s-]/g, '')
  if (before.length >= GROUP_LENGTH && compact.length === before.length + 1) {
    return { groups, focus: index }
  }
  const chunks = compact.match(new RegExp(`.{1,${String(GROUP_LENGTH)}}`, 'gu')) ?? []
  const filled = chunks.slice(0, GROUP_COUNT - index)
  filled.forEach((chunk, offset) => {
    next[index + offset] = chunk
  })
  const last = index + filled.length - 1
  const complete = (filled.at(-1)?.length ?? 0) >= GROUP_LENGTH
  return { groups: next, focus: complete ? Math.min(last + 1, GROUP_COUNT - 1) : last }
}

/** La clé saisie si ses 32 caractères sont valides, sinon null (le bouton reste inactif). */
export function recoveryKeyOf(groups: readonly string[]): RecoveryKey | null {
  try {
    return parseRecoveryKey(groups.join(''))
  } catch {
    return null
  }
}
