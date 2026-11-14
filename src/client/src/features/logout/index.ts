// API publique de la fonctionnalité logout (ADR 0012).
export type { ILogoutApi } from './api/ILogoutApi'
export { logoutApi } from './api/logoutApi'
export { LogoutApiProvider } from './model/logoutApiPort'
export { LogoutButton } from './ui/LogoutButton'
