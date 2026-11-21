import { defineMessages } from '@/shared/i18n'

// Connexion-Ordinateur, Connexion-Tablette, Connexion-Telephone, Inscription-Telephone : séparateur et boutons ;
// alertes du retour OAuth (ADR 0033, point 1).
export const oauthLoginMessages = defineMessages({
  fr: {
    or: 'ou',
    continueWith: 'Continuer avec {provider}',
    emailInUseTitle: 'Ce courriel a déjà un compte',
    emailInUseBody: 'Connectez-vous avec votre méthode habituelle.',
    rateLimitedTitle: 'Trop de tentatives',
    rateLimitedBody: 'Réessayez plus tard.',
    rejectedTitle: 'La connexion n’a pas abouti',
    rejectedBody: 'Réessayez.',
  },
})
