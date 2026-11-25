import { defineMessages } from '@/shared/i18n'

// ADR 0041 (point 4) : textes que la maquette ne donne pas, validés par Valentin le 2026-11-24.
export const conversationMessages = defineMessages({
  fr: {
    groupName: 'Nom du groupe',
    invalidGroupName: '1 à 64 caractères, dont un visible au moins.',
    notFriends: 'Vous ne pouvez ajouter que vos amis. Leur liste vient d’être mise à jour.',
    groupFull: 'Le groupe compte déjà 50 membres.',
    forbidden: 'Vous n’êtes plus administrateur de ce groupe.',
    gone: 'Cette conversation n’est plus disponible.',
  },
})
