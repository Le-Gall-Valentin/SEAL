import { defineMessages } from '@/shared/i18n'

// Inscription-Telephone et Nouveau-Mot-de-passe ; niveaux de l'ADR 0007 (point 6). Phrase sur les mots de passe
// divulgués masquée jusqu'à AUTH-12 (ADR 0007, point 9).
export const passwordMessages = defineMessages({
  fr: {
    length: '15 caractères ou plus',
    context: 'Ni votre identifiant, ni votre courriel, ni « seal »',
    blocklist: 'Absent des mots de passe les plus courants',
    composition: 'Aucune règle de composition : une phrase de passe suffit.',
    strength: 'Robustesse',
    level0: 'Insuffisant',
    level1: 'Passable',
    level2: 'Correct',
    level3: 'Bon',
    level4: 'Excellent',
  },
})
