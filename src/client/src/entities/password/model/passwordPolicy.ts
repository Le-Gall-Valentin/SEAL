/**
 * AUTH-11, en direct : même algorithme que le serveur (PROTOCOL.md, section 3 ; ADR 0019, point 2), vérifié par les
 * vecteurs partagés (ADR 0024, point 6). Le serveur tranche à l'envoi (PASSWORD_TOO_WEAK).
 */

export const MIN_PASSWORD_LENGTH = 15
export const MAX_PASSWORD_LENGTH = 128
const MIN_WORD_LENGTH = 3
const SERVICE_WORDS = ['seal', 'uqac'] as const

/** « unknown » : la liste de blocage n'a pas pu se charger ; la règle n'est jamais affichée comme respectée. */
export type RuleState = 'met' | 'unmet' | 'unknown'

/** Les trois règles affichées par Inscription-Telephone et Nouveau-Mot-de-passe. */
export interface PasswordRules {
  /** 15 à 128 points de code après NFKC, sans demi-paire isolée. */
  readonly length: RuleState
  /** « Ni votre identifiant, ni votre courriel, ni « seal » » : 15 points de code au moins une fois retirés. */
  readonly context: RuleState
  /** « Absent des mots de passe les plus courants ». */
  readonly blocklist: RuleState
}

export interface PasswordContext {
  readonly username: string
  readonly email: string
}

/** NFKC puis minuscules par la correspondance de casse par défaut d'Unicode. */
export function foldPassword(value: string): string {
  return value.normalize('NFKC').toLowerCase()
}

/** Longueur en points de code (PROTOCOL.md, section 3), et non en graphèmes ni en unités UTF-16. */
export function codePointLength(value: string): number {
  return Array.from(value).length
}

/** Partie locale du courriel, avant le premier « + » ou « @ » ; vide sans « @ ». */
export function localPartOf(email: string): string {
  const at = email.indexOf('@')
  if (at < 0) {
    return ''
  }
  const plus = email.indexOf('+')
  return email.slice(0, plus >= 0 && plus < at ? plus : at)
}

function withoutContextWords(folded: string, context: PasswordContext): string {
  const words: string[] = []
  for (const word of [context.username, localPartOf(context.email), ...SERVICE_WORDS]) {
    const candidate = foldPassword(word)
    if (codePointLength(candidate) >= MIN_WORD_LENGTH && !words.includes(candidate)) {
      words.push(candidate)
    }
  }
  // Du plus long au plus court ; le tri de JavaScript est stable : à longueur égale, l'ordre ci-dessus reste.
  const ordered = words.toSorted((a, b) => codePointLength(b) - codePointLength(a))
  let remaining = folded
  let removed = true
  while (removed) {
    removed = false
    for (const word of ordered) {
      const shorter = remaining.replaceAll(word, '')
      if (shorter.length < remaining.length) {
        remaining = shorter
        removed = true
      }
    }
  }
  return remaining
}

const state = (met: boolean): RuleState => (met ? 'met' : 'unmet')

/** @param blocklist liste de blocage repliée par foldPassword, ou null si elle n'a pas pu se charger */
export function evaluatePassword(
  password: string,
  context: PasswordContext,
  blocklist: ReadonlySet<string> | null,
): PasswordRules {
  const normalized = password.normalize('NFKC')
  const valid = normalized.isWellFormed()
  const length = codePointLength(normalized)
  const lengthMet = valid && length >= MIN_PASSWORD_LENGTH && length <= MAX_PASSWORD_LENGTH
  const folded = normalized.toLowerCase()
  const contextMet =
    valid && codePointLength(withoutContextWords(folded, context)) >= MIN_PASSWORD_LENGTH
  let blocklistState: RuleState
  if (!lengthMet) {
    blocklistState = 'unmet'
  } else if (blocklist === null) {
    blocklistState = 'unknown'
  } else {
    blocklistState = state(!blocklist.has(folded))
  }
  return { length: state(lengthMet), context: state(contextMet), blocklist: blocklistState }
}

/** Verdict du serveur : les trois règles respectées. */
export function isPasswordAccepted(rules: PasswordRules): boolean {
  return rules.length === 'met' && rules.context === 'met' && rules.blocklist === 'met'
}

/** L'envoi est permis si rien n'est refusé ; une liste de blocage non chargée laisse le serveur trancher. */
export function canSubmitPassword(rules: PasswordRules): boolean {
  return rules.length === 'met' && rules.context === 'met' && rules.blocklist !== 'unmet'
}
