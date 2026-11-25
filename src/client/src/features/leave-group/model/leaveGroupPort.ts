import { createPort } from '@/shared/lib'
import type { ILeaveGroupApi } from '../api/ILeaveGroupApi'

// ADR 0024, point 7 : port ILeaveGroupApi, fourni par la racine de composition ou par une doublure.
const port = createPort<ILeaveGroupApi>('LeaveGroupApiProvider')

export const LeaveGroupApiProvider = port.Provider
export const useLeaveGroupApi = port.usePort
