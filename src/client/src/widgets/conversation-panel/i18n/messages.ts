import { defineMessages } from '@/shared/i18n'

// Maquettes Conv-Ordinateur, Conv-Telephone-Fil et Etats ; fil vide et annonce GRP-05 : ADR 0041 (point 4).
export const conversationPanelMessages = defineMessages({
  fr: {
    info: 'Infos du groupe',
    back: 'Retour',
    you: 'vous',
    emptyTitle: 'Aucun message',
    emptyText: 'Les messages de cette conversation apparaîtront ici.',
    arrival: 'Vous ne voyez que les messages envoyés depuis votre arrivée dans le groupe.',
    sealedTitle: 'Vos conversations sont scellées',
    sealedText: 'Choisissez une conversation, ou commencez-en une nouvelle avec un ami.',
    newConversation: 'Nouvelle conversation',
  },
})
