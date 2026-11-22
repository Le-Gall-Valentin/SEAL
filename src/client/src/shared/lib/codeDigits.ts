// AUTH-14, ACC-02 : saisie d'un code à 6 chiffres, partagée par la vérification et le changement de courriel
// (ADR 0035).

/** AUTH-14 : code à 6 chiffres. */
export const CODE_LENGTH = 6

/**
 * Saisie dans la case index : chaque chiffre remplit une case, à partir de celle-ci (un code collé ou proposé par le
 * système remplit les suivantes) ; tout autre caractère est ignoré. Le focus va à la case qui suit le dernier chiffre.
 */
export function enterDigits(
  cells: readonly string[],
  index: number,
  input: string,
): { cells: string[]; focus: number } {
  const next = [...cells]
  const digits = input.replace(/\D/g, '').slice(0, CODE_LENGTH - index)
  Array.from(digits).forEach((digit, offset) => {
    next[index + offset] = digit
  })
  return {
    cells: next,
    focus: digits === '' ? index : Math.min(index + digits.length, CODE_LENGTH - 1),
  }
}
