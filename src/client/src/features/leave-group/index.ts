// API publique de la tranche leave-group (ADR 0012) : quitter un groupe (GRP-03, ADR 0041).
export type { ILeaveGroupApi } from './api/ILeaveGroupApi'
export { leaveGroupApi } from './api/leaveGroupApi'
export { LeaveGroupApiProvider } from './model/leaveGroupPort'
export { LeaveGroupDialog } from './ui/LeaveGroupDialog'
