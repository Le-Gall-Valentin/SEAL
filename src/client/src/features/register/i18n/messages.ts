import { defineMessages } from '@/shared/i18n'

// Inscription-Telephone ; « Déjà pris. » et suggestions d'Identifiant-OAuth ; erreurs de champ de l'ADR 0024 (point 9).
export const registerMessages = defineMessages({
  fr: {
    email: 'Courriel',
    invalidEmail: 'Adresse invalide.',
    username: 'Identifiant',
    available: 'Disponible',
    fromEmail: '· Proposé à partir de votre courriel.',
    taken: 'Déjà pris.',
    invalidUsername: '3 à 20 caractères : minuscules, chiffres, et « . » ou « _ » entre deux.',
    reserved: 'Réservé.',
    suggestions: 'Suggestions :',
    finalTitle: 'Identifiant définitif.',
    finalBody: 'Il ne pourra plus être modifié après la création du compte.',
    displayName: 'Nom affiché',
    displayNameHint: 'Visible par vos amis. Modifiable à tout moment.',
    invalidDisplayName: '1 à 50 caractères, dont un visible au moins.',
    password: 'Mot de passe',
    submit: 'Créer mon compte',
  },
})
