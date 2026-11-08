// API publique du segment shared/i18n (ADR 0012) : tous les textes de l'interface passent par t().
import { fr } from './fr'

/** Clé d'un texte de l'interface : une clé inconnue ne compile pas. */
export type MessageKey = keyof typeof fr

export function t(key: MessageKey): string {
  return fr[key]
}
