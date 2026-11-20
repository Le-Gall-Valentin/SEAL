import { defineMessages } from '@/shared/i18n'

// Restauration-Appareil, mot pour mot. La section « Clé de récupération perdue ? » vient avec KEY-34 (ADR 0029,
// point 6).
export const restoreKeysPageMessages = defineMessages({
  fr: {
    title: 'Restaurer vos clés',
    text: 'Premier passage sur cet appareil : saisissez votre clé de récupération. Votre identité reste la même, vos contacts ne verront aucune alerte.',
  },
})
