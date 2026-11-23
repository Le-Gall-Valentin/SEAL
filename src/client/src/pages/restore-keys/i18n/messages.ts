import { defineMessages } from '@/shared/i18n'

// Restauration-Appareil, mot pour mot. Confirmation de la réinitialisation, absente de la maquette : ADR 0036, point 3.
export const restoreKeysPageMessages = defineMessages({
  fr: {
    title: 'Restaurer vos clés',
    text: 'Premier passage sur cet appareil : saisissez votre clé de récupération. Votre identité reste la même, vos contacts ne verront aucune alerte.',
    lostTitle: 'Clé de récupération perdue ?',
    lostText:
      'Réinitialiser votre identité crée de nouvelles clés : vos anciens messages deviendront illisibles et vos contacts verront que votre clé a changé.',
    reset: 'Réinitialiser mon identité…',
    confirmTitle: 'Réinitialiser votre identité ?',
    confirmSubtitle: 'Cette action est irréversible.',
    confirmText:
      'Vos anciens messages deviendront illisibles et vos contacts verront que votre clé a changé. Une nouvelle clé de récupération vous sera remise.',
    confirmSubmit: 'Réinitialiser',
  },
})
