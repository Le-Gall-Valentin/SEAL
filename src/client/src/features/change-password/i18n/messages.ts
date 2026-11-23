import { defineMessages } from '@/shared/i18n'

// Carte Mot de passe de Parametres-Ordinateur ; dialogue validé par Valentin (ADR 0035, point 1).
export const passwordChangeMessages = defineMessages({
  fr: {
    title: 'Mot de passe',
    description: 'Sert à vous connecter. Vos clés restent protégées par votre clé de récupération.',
    open: 'Changer le mot de passe',
    dialogTitle: 'Changer le mot de passe',
    current: 'Mot de passe actuel',
    next: 'Nouveau mot de passe',
    signOut: 'Déconnecter mes autres sessions',
    submit: 'Changer le mot de passe',
    invalidCurrent: 'Mot de passe actuel incorrect.',
    changed: 'Mot de passe changé.',
  },
})
