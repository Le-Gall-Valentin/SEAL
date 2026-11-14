// API publique de la tranche account (ADR 0012) : règles des champs du compte, comme le serveur.
export { DISPLAY_NAME_MAX_LENGTH, isDisplayNameValid } from './model/displayName'
export { EMAIL_MAX_LENGTH, isEmailWellFormed, localPartOf } from './model/email'
export {
  isReservedUsername,
  isUsernameWellFormed,
  USERNAME_MAX_LENGTH,
  usernameSuggestionFrom,
} from './model/username'
