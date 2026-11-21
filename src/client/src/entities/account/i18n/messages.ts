import { defineMessages } from '@/shared/i18n'

// Inscription-Telephone et Identifiant-OAuth : champ de l'identifiant, « Déjà pris. », suggestions et avertissement
// « Identifiant définitif. » (ADR 0024, point 9 ; ADR 0033).
export const accountMessages = defineMessages({
  fr: {
    username: 'Identifiant',
    available: 'Disponible',
    fromEmail: '· Proposé à partir de votre courriel.',
    taken: 'Déjà pris.',
    invalidUsername: '3 à 20 caractères : minuscules, chiffres, et « . » ou « _ » entre deux.',
    reserved: 'Réservé.',
    suggestions: 'Suggestions :',
    finalTitle: 'Identifiant définitif.',
    finalBody: 'Il ne pourra plus être modifié après la création du compte.',
  },
})
