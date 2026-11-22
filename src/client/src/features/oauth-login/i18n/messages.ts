import { defineMessages } from '@/shared/i18n'

// Connexion-Ordinateur, Connexion-Tablette, Connexion-Telephone, Inscription-Telephone : alertes du retour OAuth
// (ADR 0033, point 1) ; séparateur et boutons dans entities/oauth-provider.
export const oauthLoginMessages = defineMessages({
  fr: {
    emailInUseTitle: 'Ce courriel a déjà un compte',
    emailInUseBody: 'Connectez-vous avec votre méthode habituelle.',
    rateLimitedTitle: 'Trop de tentatives',
    rateLimitedBody: 'Réessayez plus tard.',
    rejectedTitle: 'La connexion n’a pas abouti',
    rejectedBody: 'Réessayez.',
  },
})
