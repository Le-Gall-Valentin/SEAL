import { defineMessages } from '@/shared/i18n'

// ADR 0041 (points 3 et 4) : confirmation que la maquette ne dessine pas, validée par Valentin le 2026-11-24.
export const leaveGroupMessages = defineMessages({
  fr: {
    title: 'Quitter {group} ?',
    text: 'Vous ne recevrez plus les nouveaux messages de ce groupe.',
    submit: 'Quitter le groupe',
  },
})
