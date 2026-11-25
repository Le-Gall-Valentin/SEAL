import { defineMessages } from '@/shared/i18n'

// Maquettes Conv-Ordinateur et Conv-Tablette ; méta sans créateur et singuliers : ADR 0041 (points 4 et 5).
export const groupDetailsMessages = defineMessages({
  fr: {
    title: 'Infos du groupe',
    closePanel: 'Fermer le panneau',
    close: 'Fermer',
    meta: '{count} membres · Créé le {date}',
    metaOne: '1 membre · Créé le {date}',
    rename: 'Renommer le groupe',
    encryption: 'Les messages sont chiffrés pour chaque membre et signés par leur auteur.',
    members: '{count} membres',
    membersOne: '1 membre',
    add: 'Ajouter des membres',
    addHint:
      'Vous pouvez ajouter uniquement vos amis. Les membres ne sont pas forcément amis entre eux.',
    you: 'Vous',
    admin: 'Admin',
    notFriend: 'Pas dans vos amis',
    remove: 'Retirer {name} du groupe',
    leave: 'Quitter le groupe',
  },
})
