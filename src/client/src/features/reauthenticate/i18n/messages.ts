import { defineMessages } from '@/shared/i18n'

// Dialogue de ré-authentification, absent de la maquette : textes validés par Valentin (ADR 0035, point 1).
export const reauthMessages = defineMessages({
  fr: {
    title: 'Confirmez votre identité',
    subtitle: 'Cette action est protégée.',
    passwordIntro: 'Saisissez votre mot de passe pour continuer. La confirmation vaut 10 minutes.',
    providerIntro: 'Reconnectez-vous avec le fournisseur lié à votre compte pour continuer.',
    password: 'Mot de passe',
    submit: 'Confirmer',
    or: 'ou',
    continueWith: 'Continuer avec {provider}',
    invalidPassword: 'Mot de passe incorrect.',
    rejected: 'La confirmation par le fournisseur a échoué. Réessayez.',
    rateLimitedTitle: 'Trop de tentatives',
    rateLimitedBody: 'Réessayez plus tard.',
  },
})
