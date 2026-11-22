// API publique de la fonctionnalité change-email (ACC-02, ADR 0012).
export type { IEmailChangeApi } from './api/IEmailChangeApi'
export { emailChangeApi } from './api/emailChangeApi'
export { EmailChangeApiProvider } from './model/emailChangeApiPort'
export { EmailCard } from './ui/EmailCard'
