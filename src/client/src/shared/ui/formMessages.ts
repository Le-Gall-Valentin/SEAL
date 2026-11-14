import { defineMessages } from '@/shared/i18n'

// Champs des écrans d'authentification ; « Trop de tentatives » de Connexion-Telephone et d'Etats ; erreurs que la
// maquette ne dessine pas, et « 1 minute » (ADR 0024, point 8).
export const formMessages = defineMessages({
  fr: {
    showPassword: 'Afficher le mot de passe',
    network: 'Connexion impossible. Vérifiez votre réseau puis réessayez.',
    unexpected: 'Une erreur inattendue est survenue. Réessayez.',
    rateLimitedTitle: 'Trop de tentatives',
    retryIn: 'Réessayez dans',
    oneMinute: '1 minute',
    minutes: '{count} minutes',
    period: '.',
  },
})
