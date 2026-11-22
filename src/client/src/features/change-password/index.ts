// API publique de la fonctionnalité change-password (ACC-01, ADR 0012).
export type { IPasswordChangeApi } from './api/IPasswordChangeApi'
export { passwordChangeApi } from './api/passwordChangeApi'
export { PasswordChangeApiProvider } from './model/passwordChangeApiPort'
export { PasswordCard } from './ui/PasswordCard'
