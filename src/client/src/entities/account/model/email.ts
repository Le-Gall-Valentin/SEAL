/** RFC 5321, chemin complet (ADR 0019, point 5). */
export const EMAIL_MAX_LENGTH = 254
const LOCAL_PART_MAX_LENGTH = 64
const LABEL_MAX_LENGTH = 63
const ATEXT = /^[A-Za-z0-9!#$%&'*+\-/=?^_`{|}~.]$/u
/** Caractères non ASCII refusés : contrôle, demi-paire, non attribué, usage privé, format, séparateurs. */
const FORBIDDEN_NON_ASCII = /^[\p{Cc}\p{Cs}\p{Cn}\p{Co}\p{Cf}\p{Zl}\p{Zp}\p{Zs}]$/u
const LABEL = /^[A-Za-z0-9-]+$/

function isLocalPartCharacter(char: string): boolean {
  return char.charCodeAt(0) < 0x80 ? ATEXT.test(char) : !FORBIDDEN_NON_ASCII.test(char)
}

function isLabel(label: string): boolean {
  return (
    label.length > 0 &&
    label.length <= LABEL_MAX_LENGTH &&
    !label.startsWith('-') &&
    !label.endsWith('-') &&
    LABEL.test(label)
  )
}

/**
 * Forme d'une adresse, comme EmailFormat côté serveur (ADR 0019, point 5), sur la saisie normalisée en NFC : partie
 * locale en « dot-atom », étendue aux caractères non ASCII imprimables ; domaine en libellés ASCII, deux au moins.
 */
export function isEmailWellFormed(raw: string): boolean {
  const email = raw.normalize('NFC')
  if (email.length > EMAIL_MAX_LENGTH) {
    return false
  }
  const at = email.indexOf('@')
  if (at <= 0 || email.indexOf('@', at + 1) >= 0) {
    return false
  }
  const local = email.slice(0, at)
  const labels = email.slice(at + 1).split('.')
  return (
    local.length <= LOCAL_PART_MAX_LENGTH &&
    !local.startsWith('.') &&
    !local.endsWith('.') &&
    !local.includes('..') &&
    Array.from(local).every(isLocalPartCharacter) &&
    labels.length >= 2 &&
    labels.every(isLabel)
  )
}

/** Partie locale du courriel, avant le premier « + » ou « @ » ; vide sans « @ » (AUTH-04, AUTH-11). */
export function localPartOf(email: string): string {
  const at = email.indexOf('@')
  if (at < 0) {
    return ''
  }
  const plus = email.indexOf('+')
  return email.slice(0, plus >= 0 && plus < at ? plus : at)
}
