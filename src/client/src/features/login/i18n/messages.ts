import { defineMessages } from '@/shared/i18n'

// Connexion-Ordinateur, Connexion-Tablette et Connexion-Telephone ; « 1 minute » d'après l'ADR 0024, point 8.
export const loginMessages = defineMessages({
  fr: {
    login: 'Identifiant ou courriel',
    password: 'Mot de passe',
    forgotPassword: 'Mot de passe oublié ?',
    submit: 'Se connecter',
    invalidCredentials: 'Identifiant ou mot de passe incorrect.',
    rateLimitedTitle: 'Trop de tentatives',
    retryIn: 'Réessayez dans',
    oneMinute: '1 minute',
    minutes: '{count} minutes',
    resetHint: '. Vous pouvez aussi réinitialiser votre mot de passe.',
  },
})
