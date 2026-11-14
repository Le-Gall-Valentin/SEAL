// API publique de la tranche password (ADR 0012).
export type { IPasswordBlocklist } from './api/passwordBlocklist'
export { passwordBlocklist } from './api/passwordBlocklist'
export { PasswordBlocklistProvider } from './model/PasswordBlocklistProvider'
export {
  canSubmitPassword,
  evaluatePassword,
  isPasswordAccepted,
  MAX_PASSWORD_LENGTH,
} from './model/passwordPolicy'
export type { PasswordRules, RuleState } from './model/passwordPolicy'
export { passwordStrength } from './model/passwordStrength'
export type { PasswordStrength } from './model/passwordStrength'
export { usePasswordBlocklist } from './model/usePasswordBlocklist'
export { PasswordRulesList } from './ui/PasswordRulesList'
export { StrengthMeter } from './ui/StrengthMeter'
