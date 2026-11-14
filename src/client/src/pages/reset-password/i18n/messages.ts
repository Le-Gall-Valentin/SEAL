import { defineMessages } from '@/shared/i18n'

// Nouveau-Mot-de-passe, navigateur déjà connecté (ADR 0024, point 9).
export const resetPasswordPageMessages = defineMessages({
  fr: {
    title: 'Nouveau mot de passe',
    connected: 'Vous êtes connecté. Déconnectez-vous pour choisir un nouveau mot de passe.',
  },
})
