import { defineMessages } from '@/shared/i18n'

// Verification-Courriel ; « Code envoyé à … » de l'écran Etats ; états absents de la maquette : ADR 0024, point 9.
export const verifyEmailMessages = defineMessages({
  fr: {
    code: 'Code de vérification',
    codeBox: 'Code de vérification — {index}/6',
    expiresIn: 'Expire dans',
    attempts: '5 essais par code',
    submit: 'Vérifier',
    resend: 'Renvoyer le code',
    resendsMany: '{count} renvois restants cette heure',
    resendsOne: '1 renvoi restant cette heure',
    resendsNone: 'Aucun renvoi restant cette heure',
    sent: 'Code envoyé à {email}.',
    inactive: 'Votre compte reste inactif tant que ce courriel n’est pas vérifié.',
    change: 'Modifier le courriel',
    email: 'Courriel',
    invalidEmail: 'Adresse invalide.',
    sendCode: 'Envoyer le code',
    cancel: 'Annuler',
    invalidCode: 'Code incorrect.',
    expired: 'Ce code a expiré. Renvoyez-en un nouveau.',
  },
})
