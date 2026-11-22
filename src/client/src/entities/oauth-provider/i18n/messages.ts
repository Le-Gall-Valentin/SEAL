import { defineMessages } from '@/shared/i18n'

// Noms des fournisseurs, tels que les écrivent la maquette et les chartes des fournisseurs (ADR 0033) ; séparateur et
// boutons de Connexion-*, d'Inscription-Telephone et de la ré-authentification (ADR 0035).
export const oauthProviderMessages = defineMessages({
  fr: {
    google: 'Google',
    github: 'GitHub',
    or: 'ou',
    continueWith: 'Continuer avec {provider}',
  },
})
