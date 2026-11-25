import { createPort } from '@/shared/lib'
import type { IAddMembersApi } from '../api/IAddMembersApi'

// ADR 0024, point 7 : port IAddMembersApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IAddMembersApi>('AddMembersApiProvider')

export const AddMembersApiProvider = port.Provider
export const useAddMembersApi = port.usePort
