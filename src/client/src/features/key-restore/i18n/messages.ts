import { defineMessages } from '@/shared/i18n'

// Restauration-Appareil, mot pour mot ; refus absents de la maquette : ADR 0029, point 5.
export const keyRestoreMessages = defineMessages({
  fr: {
    label: 'Clé de récupération',
    groupBox: 'Groupe {index} sur 8',
    hint: '32 caractères en 8 groupes de 4, sans I, L, O ni U.',
    submit: 'Restaurer mes clés',
    localOnly: 'Le déchiffrement se fait ici : votre clé n’est jamais envoyée au serveur.',
    wrongKey: 'Cette clé de récupération ne correspond pas à votre coffre.',
    invalidVault:
      'Le coffre reçu du serveur est invalide : vos clés n’ont pas été restaurées. Prévenez l’administrateur du serveur.',
  },
})
