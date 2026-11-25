// API publique de la tranche create-group (ADR 0012) : créer un groupe d'amis (CNV-03, GRP-01, GRP-02, ADR 0041).
export type { ICreateGroupApi } from './api/ICreateGroupApi'
export { createGroupApi } from './api/createGroupApi'
export { CreateGroupApiProvider } from './model/createGroupPort'
export { useCreateGroup } from './model/useCreateGroup'
