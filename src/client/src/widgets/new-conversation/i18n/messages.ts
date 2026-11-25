import { defineMessages } from '@/shared/i18n'

// Maquette Nouvelle-Conversation ; « Ouvrir la conversation » vient de l'ADR 0041 (point 4).
export const newConversationMessages = defineMessages({
  fr: {
    title: 'Nouvelle conversation',
    kind: 'Type de conversation',
    direct: 'Message direct',
    group: 'Groupe',
    members: 'Membres',
    open: 'Ouvrir la conversation',
    create: 'Créer le groupe ({count})',
  },
})
