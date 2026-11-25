import { defineMessages } from '@/shared/i18n'

// Maquettes Conv-Ordinateur, Conv-Tablette, Conv-Telephone-Liste et Etats (CNV-04, CNV-10).
export const conversationListMessages = defineMessages({
  fr: {
    title: 'Conversations',
    list: 'Liste des conversations',
    newShort: 'Nouvelle',
    newConversation: 'Nouvelle conversation',
    search: 'Rechercher une conversation',
    searchShort: 'Rechercher',
    loading: 'Liste et fil de messages en cours de chargement',
    emptyTitle: 'Aucune conversation',
    emptyText: 'Écrivez à un ami pour commencer. Tout sera chiffré de bout en bout.',
    noMatchTitle: 'Aucun résultat',
    noMatch: 'Aucune conversation ne correspond à « {query} ».',
    clearSearch: 'Effacer la recherche',
  },
})
