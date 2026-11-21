import type { ReactNode } from 'react'
import { type IUsernameAvailabilityApi, UsernameAvailabilityApiProvider } from '@/entities/account'
import { AuthorityApiProvider, type IAuthorityApi } from '@/entities/authority'
import { type IKeysApi, KeysApiProvider } from '@/entities/keyring'
import { type IPasswordBlocklist, PasswordBlocklistProvider } from '@/entities/password'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import { type ILoginApi, LoginApiProvider } from '@/features/login'
import { type ILogoutApi, LogoutApiProvider } from '@/features/logout'
import { type IPasswordResetApi, PasswordResetApiProvider } from '@/features/password-reset'
import { type IRegisterApi, RegisterApiProvider } from '@/features/register'
import { type IVerifyEmailApi, VerifyEmailApiProvider } from '@/features/verify-email'

/** Les ports des tranches ; la racine de composition leur donne leurs adaptateurs, les tests des doublures. */
export interface Apis {
  readonly session: ISessionApi
  readonly authority: IAuthorityApi
  readonly keys: IKeysApi
  readonly passwordBlocklist: IPasswordBlocklist
  readonly usernameAvailability: IUsernameAvailabilityApi
  readonly login: ILoginApi
  readonly register: IRegisterApi
  readonly verifyEmail: IVerifyEmailApi
  readonly passwordReset: IPasswordResetApi
  readonly logout: ILogoutApi
}

interface ApiProvidersProps {
  readonly apis: Apis
  readonly children: ReactNode
}

/** Injecte chaque port dans sa tranche (ADR 0024, point 7). */
export function ApiProviders({ apis, children }: ApiProvidersProps) {
  return (
    <SessionApiProvider api={apis.session}>
      <AuthorityApiProvider api={apis.authority}>
        <KeysApiProvider api={apis.keys}>
          <PasswordBlocklistProvider api={apis.passwordBlocklist}>
            <UsernameAvailabilityApiProvider api={apis.usernameAvailability}>
              <LoginApiProvider api={apis.login}>
                <RegisterApiProvider api={apis.register}>
                  <VerifyEmailApiProvider api={apis.verifyEmail}>
                    <PasswordResetApiProvider api={apis.passwordReset}>
                      <LogoutApiProvider api={apis.logout}>{children}</LogoutApiProvider>
                    </PasswordResetApiProvider>
                  </VerifyEmailApiProvider>
                </RegisterApiProvider>
              </LoginApiProvider>
            </UsernameAvailabilityApiProvider>
          </PasswordBlocklistProvider>
        </KeysApiProvider>
      </AuthorityApiProvider>
    </SessionApiProvider>
  )
}
