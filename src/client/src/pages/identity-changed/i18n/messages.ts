import { defineMessages } from '@/shared/i18n'

// KEY-33, 16.1 : textes validés par Valentin, absents de la maquette (ADR 0029, point 5).
export const identityChangedPageMessages = defineMessages({
  fr: {
    title: 'Identité SEAL modifiée',
    text: 'Le serveur présente pour votre compte une autre clé d’identité que celle de cet appareil. Par sécurité, SEAL est bloqué : prévenez l’administrateur du serveur.',
  },
})
