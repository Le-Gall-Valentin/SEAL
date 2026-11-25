import { normalizeSearch } from '@/shared/lib'
import type { Friend } from '../api/IFriendsApi'

/** Le segment ui n'atteint pas api (ADR 0012) : l'ami du sélecteur lui parvient par ce modèle. */
export type { Friend }

/** FRD-20 (10.3, étape 4) : le sélecteur ne propose que les 5 amis les plus probables. */
export const PICKER_SIZE = 5

const DAY_MS = 86_400_000
/** 10.3, étape 1 : séparateurs des mots du nom affiché. */
const WORD_SEPARATORS = /[\s\-._]+/u

/** Plage `[début, fin)` dans le texte affiché, en unités UTF-16. */
export type MatchRange = readonly [number, number]

export interface RankedFriend {
  readonly friend: Friend
  /** 10.3, étape 3 : 0 identifiant qui commence, 1 mot du nom qui commence, 2 identifiant, 3 nom qui contiennent. */
  readonly tier: 0 | 1 | 2 | 3
  readonly usernameMatch: MatchRange | null
  readonly nameMatch: MatchRange | null
}

/** Texte normalisé comme `normalizeSearch`, avec, pour chaque unité UTF-16 obtenue, sa plage dans le texte d'origine. */
interface MappedText {
  readonly text: string
  readonly origins: readonly MatchRange[]
}

function mapped(original: string, offset: number): MappedText {
  let text = ''
  const origins: MatchRange[] = []
  let index = 0
  for (const char of original) {
    const start = offset + index
    index += char.length
    // Même suite que normalizeSearch, caractère par caractère, pour garder la trace de chaque position.
    const normal = char.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/gu, '')
    for (const part of normal) {
      if (text === '' && part === '@') {
        continue
      }
      text += part
      // Une origine par unité UTF-16 : `indexOf` et `query.length`, qui indexent `origins`, comptent en unités.
      for (let unit = 0; unit < part.length; unit += 1) {
        origins.push([start, offset + index])
      }
    }
  }
  return { text, origins }
}

/** Plage d'origine des caractères normalisés `[from, from + length)`. */
function rangeOf(map: MappedText, from: number, length: number): MatchRange | null {
  const first = map.origins[from]
  const last = map.origins[from + length - 1]
  return first && last ? [first[0], last[1]] : null
}

function words(displayName: string): MappedText[] {
  const result: MappedText[] = []
  let offset = 0
  for (const word of displayName.split(WORD_SEPARATORS)) {
    const start = displayName.indexOf(word, offset)
    if (word !== '') {
      result.push(mapped(word, start))
    }
    offset = start + word.length
  }
  return result
}

function tierOf(friend: Friend, query: string): Omit<RankedFriend, 'friend'> | null {
  if (query === '') {
    return { tier: 0, usernameMatch: null, nameMatch: null }
  }
  const username = mapped(friend.username, 0)
  const name = mapped(friend.displayName, 0)
  const word = words(friend.displayName).find((candidate) => candidate.text.startsWith(query))
  const inUsername = username.text.indexOf(query)
  const inName = name.text.indexOf(query)
  // Maquette Nouvelle-Conversation : la saisie est surlignée partout où elle apparaît, quel que soit le palier.
  const usernameMatch = inUsername >= 0 ? rangeOf(username, inUsername, query.length) : null
  const nameMatch = word
    ? rangeOf(word, 0, query.length)
    : inName >= 0
      ? rangeOf(name, inName, query.length)
      : null
  if (inUsername === 0) {
    return { tier: 0, usernameMatch, nameMatch }
  }
  if (word) {
    return { tier: 1, usernameMatch, nameMatch }
  }
  if (inUsername > 0) {
    return { tier: 2, usernameMatch, nameMatch }
  }
  return inName >= 0 ? { tier: 3, usernameMatch, nameMatch } : null
}

/**
 * 10.3 : `0,6 · ln(1 + n30) / ln(1 + max n30) + 0,4 · 2^(−Δ/7)`, Δ en jours fractionnaires depuis la dernière
 * interaction. Sans interaction, le second terme vaut 0 ; si personne n'a échangé, le premier aussi (ADR 0041, point 9).
 */
export function affinity(friend: Friend, maxCount30d: number, now: Date): number {
  const volume =
    maxCount30d > 0 ? Math.log1p(friend.interactionCount30d) / Math.log1p(maxCount30d) : 0
  const last = friend.lastInteractionAt
  const recency =
    last === null ? 0 : 2 ** (-Math.max(0, now.getTime() - last.getTime()) / DAY_MS / 7)
  return 0.6 * volume + 0.4 * recency
}

/**
 * FRD-20 : classe les amis pour la saisie, hors de `excluded` (déjà choisis ou déjà membres), par palier, affinité
 * décroissante puis identifiant, et garde les 5 premiers. Rien ne part au serveur (10.3).
 */
export function rankFriends(
  friends: readonly Friend[],
  query: string,
  excluded: ReadonlySet<string>,
  now: Date,
): RankedFriend[] {
  const wanted = normalizeSearch(query)
  const maxCount30d = Math.max(0, ...friends.map((friend) => friend.interactionCount30d))
  return friends
    .filter((friend) => !excluded.has(friend.userId))
    .flatMap((friend) => {
      const match = tierOf(friend, wanted)
      return match ? [{ ...match, friend, score: affinity(friend, maxCount30d, now) }] : []
    })
    .sort(
      (a, b) =>
        a.tier - b.tier ||
        b.score - a.score ||
        (a.friend.username < b.friend.username
          ? -1
          : a.friend.username > b.friend.username
            ? 1
            : 0),
    )
    .slice(0, PICKER_SIZE)
    .map(({ friend, tier, usernameMatch, nameMatch }) => ({
      friend,
      tier,
      usernameMatch,
      nameMatch,
    }))
}
