import { defineMessages } from '@/shared/i18n'

// Amis-Ordinateur, Amis-Tablette, Amis-Telephone ; état « compte neuf » d'Etats ; textes non dessinés : ADR 0038,
// point 2.
export const friendsPageMessages = defineMessages({
  fr: {
    title: 'Amis',
    sections: 'Sections',
    tabFriends: 'Amis',
    tabInvitations: 'Invitations',
    oneInvitationPending: '1 invitation en attente',
    invitationsPending: '{count} invitations en attente',
    search: 'Rechercher un ami',
    oneFriend: '1 ami',
    friendCount: '{count} amis',
    moreActions: 'Plus d’actions — {name}',
    actions: 'Actions — {name}',
    remove: 'Retirer des amis',
    addFriend: 'Ajouter un ami',
    received: 'Reçues',
    sent: 'Envoyées',
    noReceived: 'Aucune invitation reçue.',
    noSent: 'Aucune invitation envoyée.',
    noMatchTitle: 'Aucun résultat',
    noMatch: 'Aucun ami ne correspond à « {query} ».',
    clearSearch: 'Effacer la recherche',
    emptyTitle: 'Pas encore d’amis',
    emptyText: 'Invitez quelqu’un avec son identifiant. Vous ne pouvez écrire qu’à vos amis.',
  },
})
