import { defineMessages } from '@/shared/i18n'

// Inscription-Telephone et Nouveau-Mot-de-passe ; niveaux de l'ADR 0007 (point 6). Phrase sur les mots de passe
// divulgués masquée jusqu'à AUTH-12 (ADR 0007, point 9) ; état des règles pour les lecteurs d'écran et confirmation
// différente : ADR 0024, points 9 et 10.
export const passwordMessages = defineMessages({
  fr: {
    length: '15 caractères ou plus',
    context: 'Ni votre identifiant, ni votre courriel, ni « seal »',
    blocklist: 'Absent des mots de passe les plus courants',
    composition: 'Aucune règle de composition : une phrase de passe suffit.',
    met: 'respectée',
    unmet: 'non respectée',
    confirm: 'Confirmer le mot de passe',
    mismatch: 'Les mots de passe ne correspondent pas.',
    strength: 'Robustesse',
    level0: 'Insuffisant',
    level1: 'Passable',
    level2: 'Correct',
    level3: 'Bon',
    level4: 'Excellent',
  },
})
