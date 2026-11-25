/** AUTH-10, en points de code après NFC. */
export const DISPLAY_NAME_MAX_LENGTH = 50

/** SEC-01 : contrôle, demi-paire, usage privé (ADR 0040), séparateurs de ligne et de paragraphe. */
const FORBIDDEN = /[\p{Cc}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/u
/** Point non attribué dans les tables d'Unicode du navigateur, qui peuvent être plus anciennes que celles du serveur. */
const UNASSIGNED = /\p{Cn}/u
/**
 * ADR 0040 : caractères de format, dont ceux de direction (SEC-01) et les invisibles (U+200B, U+FEFF, étiquettes…) ;
 * seuls ZWNJ (U+200C) et ZWJ (U+200D) restent permis.
 */
const FORMAT = /\p{Cf}/gu
const JOINERS: ReadonlySet<number> = new Set([0x200c, 0x200d])
/** Lettre, chiffre, symbole ou ponctuation, hors des caractères de remplissage qui s'affichent vides. */
const VISIBLE = /[\p{L}\p{N}\p{S}\p{P}]/u
const FILLERS = /[\u115F\u1160\u3164\uFFA0\u2800]/gu

/**
 * ADR 0024, point 9 : le nom affiché est signalé une fois le champ quitté, et pas vide, ou tant qu'il garde la valeur que
 * le serveur a refusée.
 */
export function isDisplayNameShownInvalid({
  value,
  touched,
  refused,
}: {
  readonly value: string
  readonly touched: boolean
  readonly refused: string | undefined
}): boolean {
  return (touched && value !== '' && !isDisplayNameValid(value)) || refused === value
}

/** Comme DisplayNameRule côté serveur : 1 à 50 points de code, rien de piégé, un caractère visible au moins. */
export function isDisplayNameValid(raw: string): boolean {
  return isNameValid(raw, DISPLAY_NAME_MAX_LENGTH)
}

/**
 * Même règle à une autre longueur, comme `DisplayNameRule.isValid(value, maxLength)` : GRP-01 et l'ADR 0041 (point 9)
 * la reprennent pour le nom d'un groupe, sur 64 points de code.
 */
export function isNameValid(raw: string, maxLength: number): boolean {
  const name = raw.normalize('NFC')
  return !UNASSIGNED.test(name) && followsStableRule(name, maxLength, hasVisibleCharacter(name))
}

/** ADR 0042 : nom affiché reçu du serveur, à la règle de lecture. */
export function isDisplayNameReadable(raw: string): boolean {
  return isNameReadable(raw, DISPLAY_NAME_MAX_LENGTH)
}

/**
 * ADR 0042 : un nom reçu du serveur (ami, membre, session, nom de groupe) garde les refus qui ne dépendent pas de la
 * version d'Unicode ; un point que le navigateur ne connaît pas encore compte comme visible, le serveur l'ayant vérifié
 * dans des tables plus récentes.
 */
export function isNameReadable(raw: string, maxLength: number): boolean {
  const name = raw.normalize('NFC')
  return followsStableRule(name, maxLength, hasVisibleCharacter(name) || UNASSIGNED.test(name))
}

/** Longueur en points de code, et ni contrôle, ni séparateur, ni demi-paire, ni usage privé, ni format caché. */
function followsStableRule(name: string, maxLength: number, visible: boolean): boolean {
  const length = Array.from(name).length
  return (
    visible && length >= 1 && length <= maxLength && !FORBIDDEN.test(name) && !hasHiddenFormat(name)
  )
}

/** Au moins une lettre, un chiffre, un symbole ou une ponctuation, hors des caractères de remplissage (ADR 0040). */
export function hasVisibleCharacter(text: string): boolean {
  return VISIBLE.test(text.replace(FILLERS, ''))
}

function hasHiddenFormat(name: string): boolean {
  return Array.from(name.matchAll(FORMAT)).some(
    (match) => !JOINERS.has(match[0].codePointAt(0) ?? 0),
  )
}
