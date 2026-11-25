import { defineMessages } from '@/shared/i18n'

// ADR 0041 (points 3 et 4) : confirmation que la maquette ne dessine pas, validée par Valentin le 2026-11-24.
export const removeMemberMessages = defineMessages({
  fr: {
    title: 'Retirer {name} du groupe ?',
    text: '{name} ne recevra plus les nouveaux messages du groupe.',
    submit: 'Retirer',
  },
})
