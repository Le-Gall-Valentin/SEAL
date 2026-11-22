// API publique de la tranche account (ADR 0012) : règles des champs du compte, comme le serveur.
export {
  DISPLAY_NAME_MAX_LENGTH,
  isDisplayNameShownInvalid,
  isDisplayNameValid,
} from './model/displayName'
export { refusedAccountField } from './model/refusedField'
export { useUsernameChoice } from './model/useUsernameChoice'
export { EMAIL_MAX_LENGTH, isEmailWellFormed, localPartOf } from './model/email'
export { initialsOf } from './model/initials'
export {
  isReservedUsername,
  isUsernameWellFormed,
  USERNAME_MAX_LENGTH,
  usernameSuggestionFrom,
} from './model/username'
export { DISPLAY_NAME_SCHEMA, EMAIL_SCHEMA, USERNAME_SCHEMA } from './model/schemas'
export type { IUsernameAvailabilityApi, UsernameAvailability } from './api/IUsernameAvailabilityApi'
export { usernameAvailabilityApi } from './api/usernameAvailabilityApi'
export { UsernameAvailabilityApiProvider } from './model/usernameAvailabilityPort'
export type { UsernameStatus } from './model/useUsernameAvailability'
export { isUsernameSubmittable, usernameRefusalReason } from './model/usernameProposal'
export { DisplayNameField } from './ui/DisplayNameField'
export { FinalUsernameNotice } from './ui/FinalUsernameNotice'
export { UsernameField } from './ui/UsernameField'
