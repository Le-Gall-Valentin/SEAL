// API publique de la tranche account (ADR 0012) : règles des champs du compte, comme le serveur.
export { DISPLAY_NAME_MAX_LENGTH, isDisplayNameValid } from './model/displayName'
export { EMAIL_MAX_LENGTH, isEmailWellFormed, localPartOf } from './model/email'
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
export { USERNAME_AVAILABILITY_KEY, useUsernameAvailability } from './model/useUsernameAvailability'
export type { UsernameStatus } from './model/useUsernameAvailability'
export {
  proposeUsername,
  statusAfterRefusal,
  usernameFromEmail,
  usernameRefusalReason,
} from './model/usernameProposal'
export type { UsernameRefusal } from './model/usernameProposal'
export { DisplayNameField } from './ui/DisplayNameField'
export { FinalUsernameNotice } from './ui/FinalUsernameNotice'
export { UsernameField } from './ui/UsernameField'
