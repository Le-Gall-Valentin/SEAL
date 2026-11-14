import type { PasswordRules } from './passwordPolicy'

/** SEC-01 : borne des mots de passe dans les requêtes du serveur, avant normalisation (ADR 0019, ADR 0021). */
export const PASSWORD_INPUT_MAX_LENGTH = 512

/**
 * AUTH-11 : le serveur a refusé ce mot de passe (PASSWORD_TOO_WEAK). Seule la liste de blocage pouvait échapper au
 * client (liste non chargée) : elle tombe tant que le mot de passe refusé reste saisi.
 */
export function rulesAfterRefusal(
  rules: PasswordRules,
  refusedPassword: string | null,
  password: string,
): PasswordRules {
  return refusedPassword === password && rules.blocklist !== 'unmet'
    ? { ...rules, blocklist: 'unmet' }
    : rules
}

/**
 * ADR 0024, point 9 : « Les mots de passe ne correspondent pas. », une fois la confirmation quittée, ou dès qu'elle
 * atteint la longueur du mot de passe ; jamais pendant qu'elle se tape.
 */
export function confirmMismatch({
  password,
  confirm,
  confirmTouched,
}: {
  readonly password: string
  readonly confirm: string
  readonly confirmTouched: boolean
}): boolean {
  return (
    confirm !== '' && confirm !== password && (confirmTouched || confirm.length >= password.length)
  )
}
