import { AVATAR_TINTS, type AvatarTint } from '@/shared/config'
import { normalizeSearch } from '@/shared/lib'
import type { Friend } from '../api/IFriendsApi'

const COLLATOR = new Intl.Collator('fr', { sensitivity: 'base' })

/** ADR 0038 (point 6) : par nom affiché en français, puis par identifiant. */
export function sortFriends(friends: readonly Friend[]): Friend[] {
  return [...friends].sort(
    (a, b) =>
      COLLATOR.compare(a.displayName, b.displayName) || a.username.localeCompare(b.username),
  )
}

/** ADR 0038 (point 6) : de la plus récente à la plus ancienne. */
export function newestFirst<T extends { readonly createdAt: Date }>(items: readonly T[]): T[] {
  return [...items].sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
}

/** ADR 0038 (point 6) : l'identifiant ou le nom affiché contient la saisie ; une saisie vide garde tout le monde. */
export function matchFriend(friend: Friend, query: string): boolean {
  const wanted = normalizeSearch(query)
  return (
    wanted === '' ||
    normalizeSearch(friend.username).includes(wanted) ||
    normalizeSearch(friend.displayName).includes(wanted)
  )
}

/**
 * ADR 0038 (point 7) : initiales d'un identifiant, première lettre du premier et du dernier segment séparés par `.`
 * ou `_` ; un seul segment, sa première lettre.
 */
export function usernameInitials(username: string): string {
  const segments = username.split(/[._]/u).filter((segment) => segment !== '')
  const first = segments[0]?.charAt(0) ?? ''
  const last = segments.length > 1 ? (segments.at(-1)?.charAt(0) ?? '') : ''
  return `${first}${last}`.toLocaleUpperCase('fr')
}

/** ADR 0038 (point 7) : teinte stable tirée de l'identifiant du compte, parmi les six de la maquette. */
export function tintOf(userId: string): AvatarTint {
  let hash = 0
  for (const char of userId) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }
  return AVATAR_TINTS[hash % AVATAR_TINTS.length] ?? 'moss'
}

export type RelativeAge =
  | { readonly unit: 'now' }
  | { readonly unit: 'minutes' | 'hours' | 'days'; readonly count: number }
  | { readonly unit: 'yesterday' }
  | { readonly unit: 'date'; readonly date: Date }

const MINUTE = 60_000
const HOUR = 60 * MINUTE

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

/** ADR 0038 (point 2) : âge d'une invitation, en minutes, heures, hier, jours (2 à 6), puis sa date. */
export function relativeAge(date: Date, now: Date): RelativeAge {
  const elapsed = Math.max(0, now.getTime() - date.getTime())
  if (elapsed < MINUTE) {
    return { unit: 'now' }
  }
  if (elapsed < HOUR) {
    return { unit: 'minutes', count: Math.floor(elapsed / MINUTE) }
  }
  if (elapsed < 24 * HOUR) {
    return { unit: 'hours', count: Math.floor(elapsed / HOUR) }
  }
  const days = Math.round((startOfDay(now) - startOfDay(date)) / (24 * HOUR))
  if (days <= 1) {
    return { unit: 'yesterday' }
  }
  return days <= 6 ? { unit: 'days', count: days } : { unit: 'date', date }
}
