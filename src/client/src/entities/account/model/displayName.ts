/** AUTH-10, en points de code après NFC. */
export const DISPLAY_NAME_MAX_LENGTH = 50

/** SEC-01 : contrôle, demi-paire, non attribué, séparateurs de ligne et de paragraphe, caractères de direction. */
const FORBIDDEN = /[\p{Cc}\p{Cs}\p{Cn}\p{Zl}\p{Zp}\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]/u
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

/** Comme DisplayName côté serveur : 1 à 50 points de code, rien de piégé, un caractère visible au moins. */
export function isDisplayNameValid(raw: string): boolean {
  const name = raw.normalize('NFC')
  const length = Array.from(name).length
  return (
    length >= 1 &&
    length <= DISPLAY_NAME_MAX_LENGTH &&
    !FORBIDDEN.test(name) &&
    VISIBLE.test(name.replace(FILLERS, ''))
  )
}
