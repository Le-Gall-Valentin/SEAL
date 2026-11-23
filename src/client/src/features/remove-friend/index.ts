// API publique de la tranche remove-friend (ADR 0012) : retirer un ami (FRD-11, ADR 0038).
export type { IRemoveFriendApi } from './api/IRemoveFriendApi'
export { removeFriendApi } from './api/removeFriendApi'
export { RemoveFriendApiProvider } from './model/removeFriendPort'
export { RemoveFriendDialog } from './ui/RemoveFriendDialog'
