// API publique de la tranche add-group-members (ADR 0012) : ajouter ses amis à un groupe (GRP-03, GRP-04, ADR 0041).
export type { IAddMembersApi } from './api/IAddMembersApi'
export { addMembersApi } from './api/addMembersApi'
export { AddMembersApiProvider } from './model/addMembersPort'
export { AddMembersDialog } from './ui/AddMembersDialog'
