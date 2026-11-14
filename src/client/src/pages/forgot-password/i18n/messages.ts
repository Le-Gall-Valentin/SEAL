import { defineMessages } from '@/shared/i18n'

// Mot-de-passe-oublie, sans la phrase sur OAuth (ADR 0024, point 3).
export const forgotPasswordPageMessages = defineMessages({
  fr: {
    title: 'Mot de passe oublié',
    subtitle:
      'Indiquez le courriel de votre compte : nous y enverrons un lien pour choisir un nouveau mot de passe.',
    back: 'Retour à la connexion',
  },
})
