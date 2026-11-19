import { defineMessages } from '@/shared/i18n'

// Dialogue-Cle-Recuperation, mot pour mot ; états absents de la maquette : ADR 0029, points 5 et 7. Pas de « Copier »
// (ADR 0029, point 1). Texte sous la clé confirmée au futur, sans Paramètres jusqu'à KEY-35 (ADR 0029, point 9).
export const keySetupMessages = defineMessages({
  fr: {
    step: 'Étape {current} sur 2',
    showTitle: 'Votre clé de récupération',
    showText:
      'Elle seule permet de restaurer vos clés sur un nouvel appareil. SEAL ne la connaît pas et ne pourra jamais la retrouver pour vous.',
    groupsLabel: 'Clé de récupération, 8 groupes de 4 caractères',
    download: 'Télécharger (.txt)',
    onceTitle: 'Affichée une seule fois.',
    onceText:
      'Rangez-la hors de cet appareil : sur papier ou dans un gestionnaire de mots de passe.',
    noted: 'J’ai noté ma clé',
    verifyTitle: 'Vérifiez votre clé',
    verifyText:
      'Saisissez deux groupes tirés au hasard pour confirmer que votre clé est bien notée.',
    group: 'Groupe {number}',
    wrongGroup: 'Ce groupe ne correspond pas à votre clé.',
    confirmed: 'Clé confirmée.',
    savedText: 'Votre trousseau sera sauvegardé chiffré sur le serveur, qui ne pourra pas le lire.',
    review: 'Revoir la clé',
    finish: 'Terminer',
    saveFailed: 'Vos clés n’ont pas pu être sauvegardées. Vérifiez votre réseau puis réessayez.',
    fileTitle: 'SEAL — clé de récupération',
    fileAccount: 'Compte : {username}',
  },
})
