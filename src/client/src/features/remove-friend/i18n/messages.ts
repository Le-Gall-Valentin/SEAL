import { defineMessages } from '@/shared/i18n'

// Amis-Ordinateur (« Retirer des amis ») ; confirmation : ADR 0038, point 2, effet de FRD-11.
export const removeFriendMessages = defineMessages({
  fr: {
    title: 'Retirer {name} de vos amis ?',
    text: 'Vous ne pourrez plus lui écrire. Vos groupes communs ne changent pas.',
    submit: 'Retirer',
  },
})
