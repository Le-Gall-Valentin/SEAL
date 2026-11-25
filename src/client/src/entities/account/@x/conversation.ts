// API croisée de account pour l'entité conversation (ADR 0012) : la règle du nom, reprise pour un nom de groupe
// (GRP-01, ADR 0040, ADR 0041 point 9), sa part « visible » pour un prénom (ACC-03), et les règles d'écriture des noms des membres (SEC-01, AUTH-01, ACC-03).
export { hasVisibleCharacter, isDisplayNameValid, isNameValid } from '../model/displayName'
export { initialsOf } from '../model/initials'
export { isUsernameWellFormed } from '../model/username'
