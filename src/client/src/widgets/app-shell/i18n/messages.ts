import { defineMessages } from '@/shared/i18n'

// Navigation principale de Parametres-* et Amis-* (ADR 0035, point 3) ; nom accessible de l'entrée Amis avec ses
// invitations : modèle Amis-Tablette (ADR 0038, point 2).
export const appShellMessages = defineMessages({
  fr: {
    mainNav: 'Navigation principale',
    home: 'SEAL',
    conversations: 'Conversations',
    friends: 'Amis',
    friendsOne: 'Amis, 1 invitation',
    friendsMany: 'Amis, {count} invitations',
    settings: 'Paramètres',
    profile: 'Mon profil',
  },
})
