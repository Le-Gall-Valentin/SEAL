import type { ReactNode } from 'react'
import {
  type ILoginMethodsApi,
  type IUsernameAvailabilityApi,
  LoginMethodsApiProvider,
  UsernameAvailabilityApiProvider,
} from '@/entities/account'
import { AuthorityApiProvider, type IAuthorityApi } from '@/entities/authority'
import { FriendsApiProvider, type IFriendsApi } from '@/entities/friend'
import { type IKeysApi, KeysApiProvider } from '@/entities/keyring'
import { type IOAuthProvidersApi, OAuthProvidersApiProvider } from '@/entities/oauth-provider'
import { type IPasswordBlocklist, PasswordBlocklistProvider } from '@/entities/password'
import { type ISessionApi, SessionApiProvider } from '@/entities/session'
import {
  AnswerFriendRequestApiProvider,
  type IAnswerFriendRequestApi,
} from '@/features/answer-friend-request'
import { type IProfileApi, ProfileApiProvider } from '@/features/change-display-name'
import { EmailChangeApiProvider, type IEmailChangeApi } from '@/features/change-email'
import { type IPasswordChangeApi, PasswordChangeApiProvider } from '@/features/change-password'
import { type ILoginApi, LoginApiProvider } from '@/features/login'
import { type ILogoutApi, LogoutApiProvider } from '@/features/logout'
import {
  type IOAuthRegistrationApi,
  OAuthRegistrationApiProvider,
} from '@/features/oauth-registration'
import { type IPasswordResetApi, PasswordResetApiProvider } from '@/features/password-reset'
import { type IReauthApi, ReauthApiProvider } from '@/features/reauthenticate'
import { type IRegisterApi, RegisterApiProvider } from '@/features/register'
import { type IRemoveFriendApi, RemoveFriendApiProvider } from '@/features/remove-friend'
import {
  type ISendFriendRequestApi,
  SendFriendRequestApiProvider,
} from '@/features/send-friend-request'
import { type IVerifyEmailApi, VerifyEmailApiProvider } from '@/features/verify-email'

/** Les ports des tranches ; la racine de composition leur donne leurs adaptateurs, les tests des doublures. */
export interface Apis {
  readonly session: ISessionApi
  readonly authority: IAuthorityApi
  readonly keys: IKeysApi
  readonly passwordBlocklist: IPasswordBlocklist
  readonly usernameAvailability: IUsernameAvailabilityApi
  readonly oauthProviders: IOAuthProvidersApi
  readonly login: ILoginApi
  readonly register: IRegisterApi
  readonly oauthRegistration: IOAuthRegistrationApi
  readonly verifyEmail: IVerifyEmailApi
  readonly passwordReset: IPasswordResetApi
  readonly logout: ILogoutApi
  readonly loginMethods: ILoginMethodsApi
  readonly reauth: IReauthApi
  readonly profile: IProfileApi
  readonly passwordChange: IPasswordChangeApi
  readonly emailChange: IEmailChangeApi
  readonly friends: IFriendsApi
  readonly sendFriendRequest: ISendFriendRequestApi
  readonly answerFriendRequest: IAnswerFriendRequestApi
  readonly removeFriend: IRemoveFriendApi
}

interface ApiProvidersProps {
  readonly apis: Apis
  readonly children: ReactNode
}

type Layer = (children: ReactNode) => ReactNode

/** Injecte chaque port dans sa tranche (ADR 0024, point 7) ; le premier fournisseur est le plus extérieur. */
export function ApiProviders({ apis, children }: ApiProvidersProps) {
  const layers: readonly Layer[] = [
    (inner) => <SessionApiProvider api={apis.session}>{inner}</SessionApiProvider>,
    (inner) => <AuthorityApiProvider api={apis.authority}>{inner}</AuthorityApiProvider>,
    (inner) => <KeysApiProvider api={apis.keys}>{inner}</KeysApiProvider>,
    (inner) => (
      <PasswordBlocklistProvider api={apis.passwordBlocklist}>{inner}</PasswordBlocklistProvider>
    ),
    (inner) => (
      <UsernameAvailabilityApiProvider api={apis.usernameAvailability}>
        {inner}
      </UsernameAvailabilityApiProvider>
    ),
    (inner) => (
      <OAuthProvidersApiProvider api={apis.oauthProviders}>{inner}</OAuthProvidersApiProvider>
    ),
    (inner) => <LoginApiProvider api={apis.login}>{inner}</LoginApiProvider>,
    (inner) => <RegisterApiProvider api={apis.register}>{inner}</RegisterApiProvider>,
    (inner) => (
      <OAuthRegistrationApiProvider api={apis.oauthRegistration}>
        {inner}
      </OAuthRegistrationApiProvider>
    ),
    (inner) => <VerifyEmailApiProvider api={apis.verifyEmail}>{inner}</VerifyEmailApiProvider>,
    (inner) => (
      <PasswordResetApiProvider api={apis.passwordReset}>{inner}</PasswordResetApiProvider>
    ),
    (inner) => <LogoutApiProvider api={apis.logout}>{inner}</LogoutApiProvider>,
    (inner) => <LoginMethodsApiProvider api={apis.loginMethods}>{inner}</LoginMethodsApiProvider>,
    (inner) => <ReauthApiProvider api={apis.reauth}>{inner}</ReauthApiProvider>,
    (inner) => <ProfileApiProvider api={apis.profile}>{inner}</ProfileApiProvider>,
    (inner) => (
      <PasswordChangeApiProvider api={apis.passwordChange}>{inner}</PasswordChangeApiProvider>
    ),
    (inner) => <EmailChangeApiProvider api={apis.emailChange}>{inner}</EmailChangeApiProvider>,
    (inner) => <FriendsApiProvider api={apis.friends}>{inner}</FriendsApiProvider>,
    (inner) => (
      <SendFriendRequestApiProvider api={apis.sendFriendRequest}>
        {inner}
      </SendFriendRequestApiProvider>
    ),
    (inner) => (
      <AnswerFriendRequestApiProvider api={apis.answerFriendRequest}>
        {inner}
      </AnswerFriendRequestApiProvider>
    ),
    (inner) => <RemoveFriendApiProvider api={apis.removeFriend}>{inner}</RemoveFriendApiProvider>,
  ]
  return layers.reduceRight<ReactNode>((inner, layer) => layer(inner), children)
}
