/**
 * ADR 0033, point 3 : « Google », « Google ou GitHub », « Microsoft, Google ou GitHub ». Le mot de liaison vient des
 * textes de l'écran.
 */
export function joinWithOr(names: readonly string[], or: string): string {
  if (names.length <= 1) {
    return names.join('')
  }
  return `${names.slice(0, -1).join(', ')} ${or} ${names.at(-1) ?? ''}`
}
