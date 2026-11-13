import { defineMessages } from '@/shared/i18n'

// Champs des écrans d'authentification, et erreurs que la maquette ne dessine pas (ADR 0024, point 8).
export const formMessages = defineMessages({
  fr: {
    showPassword: 'Afficher le mot de passe',
    network: 'Connexion impossible. Vérifiez votre réseau puis réessayez.',
    unexpected: 'Une erreur inattendue est survenue. Réessayez.',
  },
})
