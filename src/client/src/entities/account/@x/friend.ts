// API croisée de account pour l'entité friend (ADR 0012) : les règles d'écriture des noms, pour refuser une réponse
// qui ne les suit pas (SEC-01, AUTH-01, ACC-03, ADR 0038 point 9).
export { isDisplayNameValid } from '../model/displayName'
export { isUsernameWellFormed } from '../model/username'
