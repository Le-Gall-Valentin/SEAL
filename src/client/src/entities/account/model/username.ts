/** AUTH-01 : 3 à 20 caractères, minuscules et chiffres, « . » et « _ » isolés entre deux alphanumériques. */
const USERNAME = /^(?=.{3,20}$)[a-z0-9]+(?:[._][a-z0-9]+)*$/

export const USERNAME_MAX_LENGTH = 20
const USERNAME_MIN_LENGTH = 3
const FALLBACK = 'user'

/** AUTH-02, liste complète de l'ADR 0007 (point 5), la même que le serveur. */
const RESERVED: ReadonlySet<string> = new Set([
  'admin',
  'root',
  'system',
  'seal',
  'support',
  'security',
  'api',
  'null',
  'me',
  'uqac',
  'administrator',
  'moderator',
  'postmaster',
  'webmaster',
  'hostmaster',
  'abuse',
  'noreply',
  'help',
  'info',
  'contact',
  'anonymous',
  'deleted',
  'test',
])

export function isUsernameWellFormed(username: string): boolean {
  return USERNAME.test(username)
}

export function isReservedUsername(username: string): boolean {
  return RESERVED.has(username)
}

/** Coupe à max caractères, puis retire les séparateurs de fin. */
function truncate(value: string, max: number): string {
  return value.slice(0, max).replace(/\.+$/, '')
}

/**
 * AUTH-04, étapes 1 à 4, comme UsernameSuggestions côté serveur : partie locale, NFKD sans diacritiques, minuscules ;
 * tout autre caractère qu'une minuscule ASCII ou un chiffre devient un séparateur « . », fusionné et retiré aux bords ;
 * 20 caractères au plus ; « user » s'il en reste moins de 3.
 */
export function usernameSuggestionFrom(email: string): string {
  const at = email.indexOf('@')
  const plus = email.indexOf('+')
  const local = at < 0 ? email : email.slice(0, plus >= 0 && plus < at ? plus : at)
  const folded = local
    .normalize('NFKD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
  let base = ''
  let pendingSeparator = false
  for (const char of folded) {
    if (/^[a-z0-9]$/.test(char)) {
      if (pendingSeparator && base !== '') {
        base += '.'
      }
      pendingSeparator = false
      base += char
    } else {
      pendingSeparator = true
    }
  }
  const truncated = truncate(base, USERNAME_MAX_LENGTH)
  return truncated.length < USERNAME_MIN_LENGTH ? FALLBACK : truncated
}
