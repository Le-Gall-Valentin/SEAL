import { defineMessages } from '@/shared/i18n'

// Écran Etats, état « Erreur de chargement », sans la phrase sur le réseau (ADR 0012).
export const loadErrorStateMessages = defineMessages({
  fr: {
    title: 'Impossible de charger',
    retry: 'Réessayer',
  },
})
