import { defineMessages } from '@/shared/i18n'

// KEY-01 : titre du CDC ; texte validé par Valentin, absent de la maquette (ADR 0027, point 1).
export const unsupportedBrowserPageMessages = defineMessages({
  fr: {
    title: 'Navigateur non pris en charge',
    text: 'SEAL chiffre vos messages dans votre navigateur, et celui-ci ne propose pas les algorithmes nécessaires (Ed25519 et X25519). Mettez-le à jour ou ouvrez SEAL dans un navigateur récent.',
  },
})
