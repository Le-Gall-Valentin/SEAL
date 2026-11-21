import { defineMessages } from '@/shared/i18n'

// Inscription-Telephone, sans le champ de l'identifiant (entities/account) ; erreurs de champ de l'ADR 0024 (point 9).
export const registerMessages = defineMessages({
  fr: {
    email: 'Courriel',
    invalidEmail: 'Adresse invalide.',
    displayName: 'Nom affiché',
    displayNameHint: 'Visible par vos amis. Modifiable à tout moment.',
    invalidDisplayName: '1 à 50 caractères, dont un visible au moins.',
    password: 'Mot de passe',
    submit: 'Créer mon compte',
  },
})
