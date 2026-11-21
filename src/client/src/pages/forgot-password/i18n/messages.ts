import { defineMessages } from '@/shared/i18n'

// Mot-de-passe-oublie ; la phrase sur OAuth ne nomme que les fournisseurs configurés (ADR 0033, point 3).
export const forgotPasswordPageMessages = defineMessages({
  fr: {
    title: 'Mot de passe oublié',
    subtitle:
      'Indiquez le courriel de votre compte : nous y enverrons un lien pour choisir un nouveau mot de passe.',
    back: 'Retour à la connexion',
    or: 'ou',
    oauthOne:
      'Vous vous connectez avec {provider} ? Ce compte n’a pas de mot de passe SEAL : utilisez le bouton du fournisseur.',
    oauthMany:
      'Vous vous connectez avec {providers} ? Ces comptes n’ont pas de mot de passe SEAL : utilisez le bouton du fournisseur.',
  },
})
