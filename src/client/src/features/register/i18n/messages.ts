import { defineMessages } from '@/shared/i18n'

// Inscription-Telephone, sans les champs de l'identifiant et du nom affiché (entities/account) ; erreurs de champ de
// l'ADR 0024 (point 9).
export const registerMessages = defineMessages({
  fr: {
    email: 'Courriel',
    invalidEmail: 'Adresse invalide.',
    password: 'Mot de passe',
    submit: 'Créer mon compte',
  },
})
