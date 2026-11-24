import { PersonGlyph } from '@/shared/ui'

/** Personne et « + » du bouton d'envoi et du bouton d'ajout (Amis-Ordinateur, Amis-Telephone), 18 px. */
export function PersonPlusIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <PersonGlyph />
      <path d="M19 8v6M16 11h6" />
    </svg>
  )
}

/** Croix de « Fermer » de la feuille (Amis-Telephone), 20 px. */
export function CloseIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  )
}
