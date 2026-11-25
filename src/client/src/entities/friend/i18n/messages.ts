import { defineMessages } from '@/shared/i18n'

// ADR 0038 (point 2) : dates des invitations ; Amis-Tablette n'en dessine que trois (« il y a 2 heures », « hier »,
// « il y a 3 jours »).
export const friendMessages = defineMessages({
  fr: {
    received: 'Reçue {age}',
    sent: 'Envoyée {age}',
    now: 'à l’instant',
    oneMinute: 'il y a 1 minute',
    minutes: 'il y a {count} minutes',
    oneHour: 'il y a 1 heure',
    hours: 'il y a {count} heures',
    yesterday: 'hier',
    days: 'il y a {count} jours',
    date: 'le {date}',
    // FRD-20, maquette Nouvelle-Conversation ; ADR 0041 (point 4) pour les deux états vides.
    pickerHint: 'Seuls vos amis apparaissent ici.',
    pickerKeys: '↑ ↓ pour choisir · Entrée pour ajouter · Échap pour effacer',
    pickerBest: 'Meilleures correspondances',
    pickerList: 'Ajouter des amis',
    pickerRemove: 'Retirer {name}',
    pickerNoFriends: 'Vous n’avez pas encore d’amis.',
    pickerNoMatch: 'Aucun ami ne correspond à « {query} ».',
  },
})
