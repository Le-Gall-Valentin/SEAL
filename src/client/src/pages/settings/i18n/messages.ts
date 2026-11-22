import { defineMessages } from '@/shared/i18n'

// Parametres-Ordinateur, Parametres-Tablette, Parametres-Telephone et Parametres-Telephone-Preferences (ADR 0035).
export const settingsMessages = defineMessages({
  fr: {
    title: 'Paramètres',
    sections: 'Sections des paramètres',
    account: 'Compte',
    // ADR 0035 : « comptes liés » revient avec ACC-04 (P1) ; SEAL n'annonce pas une fonction absente.
    accountSummary: 'Profil, courriel, mot de passe',
    usernameFinal: 'Identifiant définitif',
    back: 'Paramètres',
  },
})
