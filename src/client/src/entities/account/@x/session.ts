// API croisée de account pour l'entité session (ADR 0012) : les règles d'écriture des noms, pour refuser une réponse
// de /me qui ne les suit pas (SEC-01, AUTH-01, ACC-03, ADR 0038 point 9).
export { isDisplayNameReadable } from '../model/displayName'
export { isUsernameWellFormed } from '../model/username'
