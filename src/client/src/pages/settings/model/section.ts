/**
 * Sous-page ouverte sur téléphone (ADR 0035) : `?section=compte`, que le serveur ignore. Seule « Compte » existe en
 * P0 (point 4) ; toute autre valeur vaut la liste des sections.
 */
export const SECTION_PARAM = 'section'

export type SettingsSection = 'compte'

export function sectionOf(params: URLSearchParams): SettingsSection | null {
  return params.get(SECTION_PARAM) === 'compte' ? 'compte' : null
}
