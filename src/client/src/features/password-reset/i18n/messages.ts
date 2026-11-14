import { defineMessages } from '@/shared/i18n'

// Mot-de-passe-oublie et Nouveau-Mot-de-passe, sans les clés, la double authentification ni OAuth (ADR 0024, points 3
// et 9) ; états absents de la maquette : ADR 0024, point 9.
export const passwordResetMessages = defineMessages({
  fr: {
    email: 'Courriel',
    invalidEmail: 'Adresse invalide.',
    send: 'Envoyer le lien',
    sentTitle: 'Vérifiez votre boîte de réception',
    sentBody:
      'Si un compte correspond à cette adresse, un lien vient de partir. Il est valable 30 minutes et ne sert qu’une fois.',
    sessionsInfo: 'Après la réinitialisation, toutes vos sessions seront fermées.',
    title: 'Nouveau mot de passe',
    forAccount: 'Pour',
    expiresIn: 'Ce lien expire dans',
    oneMinute: '1 minute',
    minutes: '{count} minutes',
    period: '.',
    newPassword: 'Nouveau mot de passe',
    confirm: 'Confirmer le mot de passe',
    mismatch: 'Les mots de passe ne correspondent pas.',
    sessionsNote: 'Toutes vos sessions seront fermées. Reconnectez-vous ensuite.',
    submit: 'Enregistrer le mot de passe',
    deadLink: 'Ce lien n’est plus valable.',
    requestNew: 'Demander un nouveau lien',
  },
})
