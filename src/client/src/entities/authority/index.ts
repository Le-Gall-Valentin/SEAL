// API publique de la tranche authority (ADR 0012) : Autorité SEAL épinglée (KEY-11).
export type { AuthorityView, IAuthorityApi } from './api/IAuthorityApi'
export { authorityApi } from './api/authorityApi'
export { AuthorityApiProvider } from './model/authorityApiPort'
export { KeyStoreProvider } from './model/keyStorePort'
export { useAuthorityCheck } from './model/useAuthorityCheck'
