import { codePointLength, isPasswordAccepted, type PasswordRules } from './passwordPolicy'

/** Niveaux de la jauge « Robustesse » (ADR 0007, point 6). */
export type PasswordStrength = 0 | 1 | 2 | 3 | 4

/**
 * ADR 0007, point 6 : 0 (« Insuffisant ») tant qu'une règle n'est pas respectée ; ensuite selon la longueur après NFKC,
 * en points de code : 15 à 17, 18 ou 19, 20 à 24, puis 25 et plus.
 */
export function passwordStrength(password: string, rules: PasswordRules): PasswordStrength {
  if (!isPasswordAccepted(rules)) {
    return 0
  }
  const length = codePointLength(password.normalize('NFKC'))
  if (length >= 25) {
    return 4
  }
  if (length >= 20) {
    return 3
  }
  return length >= 18 ? 2 : 1
}
