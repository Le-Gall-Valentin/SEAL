import { defineMessages } from '@/shared/i18n'

// Connexion-Ordinateur, Connexion-Tablette et Connexion-Telephone ; confirmation après une réinitialisation
// (ADR 0024, point 9).
export const loginPageMessages = defineMessages({
  fr: {
    title: 'Connexion',
    subtitle: 'Heureux de vous revoir.',
    noAccount: 'Pas encore de compte ?',
    createAccount: 'Créer un compte',
    passwordSaved: 'Mot de passe enregistré. Connectez-vous avec le nouveau.',
  },
})
