import { defineMessages } from '@/shared/i18n'

// Amis-Tablette (boutons, pastille) ; noms accessibles et invitation close : ADR 0038, point 2.
export const answerFriendRequestMessages = defineMessages({
  fr: {
    accept: 'Accepter',
    decline: 'Refuser',
    revoke: 'Révoquer',
    pending: 'En attente',
    acceptLabel: 'Accepter — {name}',
    declineLabel: 'Refuser — {name}',
    revokeLabel: 'Révoquer — {username}',
    gone: 'Cette invitation n’existe plus.',
  },
})
