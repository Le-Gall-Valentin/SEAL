// API publique de la tranche remove-group-member (ADR 0012) : retirer un membre d'un groupe (GRP-03, ADR 0041).
export type { IRemoveMemberApi } from './api/IRemoveMemberApi'
export { removeMemberApi } from './api/removeMemberApi'
export { RemoveMemberApiProvider } from './model/removeMemberPort'
export { RemoveMemberDialog } from './ui/RemoveMemberDialog'
