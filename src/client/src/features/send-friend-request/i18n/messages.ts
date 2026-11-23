import { defineMessages } from '@/shared/i18n'

// Amis-Ordinateur et Amis-Telephone (formulaire, aide, « déjà invité », identifiant inexistant), Etats (invitation
// envoyée) ; autres refus : tableau de 10.1 du CDC (ADR 0038, point 1).
export const sendFriendRequestMessages = defineMessages({
  fr: {
    title: 'Ajouter un ami',
    username: 'Identifiant',
    submit: 'Envoyer l’invitation',
    help: 'Saisissez l’identifiant exact : SEAL ne propose pas d’annuaire d’utilisateurs.',
    // Texte d'AUTH-01 de l'inscription (entities/account), recopié par tranche (ADR 0017).
    invalidUsername: '3 à 20 caractères : minuscules, chiffres, et « . » ou « _ » entre deux.',
    sent: 'Invitation envoyée à {username}.',
    notFound: 'Aucun utilisateur ne porte l’identifiant {username}.',
    incoming: '{username} vous a déjà invité. Acceptez directement !',
    accept: 'Accepter l’invitation',
    self: 'Vous ne pouvez pas vous inviter vous-même',
    alreadyFriends: 'Vous êtes déjà amis',
    alreadyPending: 'Invitation déjà envoyée',
    cannotInvite: 'Impossible d’inviter cet utilisateur',
  },
})
