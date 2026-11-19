import { defineMessages } from '@/shared/i18n'

// KEY-11 : textes validés par Valentin, absents de la maquette (ADR 0027, point 1).
export const authorityChangedPageMessages = defineMessages({
  fr: {
    title: 'Autorité SEAL modifiée',
    text: 'La clé de l’Autorité SEAL a changé depuis votre première connexion sur cet appareil. Par sécurité, SEAL est bloqué : prévenez l’administrateur du serveur.',
  },
})
