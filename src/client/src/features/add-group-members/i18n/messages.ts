import { defineMessages } from '@/shared/i18n'

// ADR 0041 (points 3 et 4) : dialogue que la maquette ne dessine pas, validé par Valentin le 2026-11-24.
export const addMembersMessages = defineMessages({
  fr: {
    title: 'Ajouter des membres',
    members: 'Membres',
    submit: 'Ajouter ({count})',
  },
})
