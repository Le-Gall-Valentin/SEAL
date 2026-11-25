// API publique de la tranche rename-group (ADR 0012) : renommer un groupe (GRP-03, ADR 0041).
export type { IRenameGroupApi } from './api/IRenameGroupApi'
export { renameGroupApi } from './api/renameGroupApi'
export { RenameGroupApiProvider } from './model/renameGroupPort'
export { RenameGroupDialog } from './ui/RenameGroupDialog'
