/** AUTH-10, en points de code après NFC. */
export const DISPLAY_NAME_MAX_LENGTH = 50

/** SEC-01 : contrôle, demi-paire, non attribué, usage privé (ADR 0040), séparateurs de ligne et de paragraphe. */
const FORBIDDEN = /[\p{Cc}\p{Cs}\p{Cn}\p{Co}\p{Zl}\p{Zp}]/u
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
  const name = raw.normalize('NFC')
  const length = Array.from(name).length
  return (
    length >= 1 &&
    length <= DISPLAY_NAME_MAX_LENGTH &&
    !FORBIDDEN.test(name) &&
    !hasHiddenFormat(name) &&
    VISIBLE.test(name.replace(FILLERS, ''))
  )
}

function hasHiddenFormat(name: string): boolean {
  return Array.from(name.matchAll(FORMAT)).some(
    (match) => !JOINERS.has(match[0].codePointAt(0) ?? 0),
  )
}
