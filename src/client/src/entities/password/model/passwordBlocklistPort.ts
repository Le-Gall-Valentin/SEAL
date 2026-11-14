import { createPort } from '@/shared/lib'
import type { IPasswordBlocklist } from '../api/passwordBlocklist'

// ADR 0024, point 7 : port IPasswordBlocklist, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<IPasswordBlocklist>('PasswordBlocklistProvider')

export const PasswordBlocklistProvider = port.Provider
export const usePasswordBlocklistApi = port.usePort
