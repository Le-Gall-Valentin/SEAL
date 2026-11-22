import { defineMessages } from '@/shared/i18n'

// Carte Courriel de Parametres-Ordinateur ; dialogue validé par Valentin (ADR 0035, point 1) ; refus : textes de
// l'inscription et de Verification-Courriel.
export const emailChangeMessages = defineMessages({
  fr: {
    title: 'Courriel',
    open: 'Changer de courriel',
    step1: 'Étape 1 sur 2',
    step2: 'Étape 2 sur 2',
    addressTitle: 'Changer de courriel',
    addressIntro:
      'Un code de vérification sera envoyé à la nouvelle adresse. Votre courriel actuel reste en place jusqu’à la confirmation.',
    newEmail: 'Nouveau courriel',
    sendCode: 'Envoyer le code',
    codeTitle: 'Saisissez le code',
    codeIntro: 'Nous avons envoyé un code à 6 chiffres à {email}. Il expire dans 10 minutes.',
    code: 'Code de vérification',
    codeBox: 'Chiffre {index} sur 6',
    resend: 'Renvoyer le code',
    confirm: 'Confirmer',
    changed: 'Courriel changé.',
    invalidEmail: 'Adresse invalide.',
    invalidCode: 'Code incorrect.',
    expired: 'Ce code a expiré. Renvoyez-en un nouveau.',
  },
})
