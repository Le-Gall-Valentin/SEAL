import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { loginMethodsApi, usernameAvailabilityApi } from '@/entities/account'
import { authorityApi } from '@/entities/authority'
import { conversationsApi } from '@/entities/conversation'
import { friendsApi } from '@/entities/friend'
import { keysApi } from '@/entities/keyring'
import { oauthProvidersApi } from '@/entities/oauth-provider'
import { passwordBlocklist } from '@/entities/password'
import { sessionApi } from '@/entities/session'
import { answerFriendRequestApi } from '@/features/answer-friend-request'
import { profileApi } from '@/features/change-display-name'
import { createGroupApi } from '@/features/create-group'
import { emailChangeApi } from '@/features/change-email'
import { passwordChangeApi } from '@/features/change-password'
import { loginApi } from '@/features/login'
import { logoutApi } from '@/features/logout'
import { oauthRegistrationApi } from '@/features/oauth-registration'
import { passwordResetApi } from '@/features/password-reset'
import { reauthApi } from '@/features/reauthenticate'
import { registerApi } from '@/features/register'
import { removeFriendApi } from '@/features/remove-friend'
import { sendFriendRequestApi } from '@/features/send-friend-request'
import { verifyEmailApi } from '@/features/verify-email'
import { startDirectApi } from '@/features/start-direct-conversation'
import { leaveGroupApi } from '@/features/leave-group'
import { removeMemberApi } from '@/features/remove-group-member'
import { renameGroupApi } from '@/features/rename-group'
import { addMembersApi } from '@/features/add-group-members'
import { LanguageProvider } from '@/shared/i18n'
import {
  ApiProviders,
  type Apis,
  bootstrap,
  createCryptoStartup,
  createQueryClient,
  CryptoGate,
  ErrorBoundary,
  routerErrorHandler,
} from './providers'
import { routes } from './router'

const router = createBrowserRouter(routes)
const queryClient = createQueryClient()

/** Racine de composition (ADR 0024, point 7) : seul endroit qui associe chaque port à son adaptateur. */
const apis: Apis = {
  session: sessionApi,
  authority: authorityApi,
  keys: keysApi,
  passwordBlocklist,
  usernameAvailability: usernameAvailabilityApi,
  oauthProviders: oauthProvidersApi,
  login: loginApi,
  register: registerApi,
  oauthRegistration: oauthRegistrationApi,
  verifyEmail: verifyEmailApi,
  passwordReset: passwordResetApi,
  logout: logoutApi,
  loginMethods: loginMethodsApi,
  reauth: reauthApi,
  profile: profileApi,
  passwordChange: passwordChangeApi,
  emailChange: emailChangeApi,
  friends: friendsApi,
  sendFriendRequest: sendFriendRequestApi,
  answerFriendRequest: answerFriendRequestApi,
  removeFriend: removeFriendApi,
  conversations: conversationsApi,
  startDirect: startDirectApi,
  createGroup: createGroupApi,
  addMembers: addMembersApi,
  renameGroup: renameGroupApi,
  removeMember: removeMemberApi,
  leaveGroup: leaveGroupApi,
}

// KEY-01, KEY-40 : Ed25519 et X25519 éprouvés, puis le jeton CSRF rechargé et le trousseau de l'appareil ouvert ; un
// navigateur refusé n'envoie aucune requête (ADR 0027, point 8).
const startCrypto = createCryptoStartup({
  onSupported: () => {
    bootstrap(apis)
  },
})

export function App() {
  return (
    // ACC-05 : le français, seule langue tant que la préférence de langue n'est pas livrée (ADR 0017).
    <LanguageProvider language="fr">
      <ErrorBoundary>
        <CryptoGate start={startCrypto}>
          <QueryClientProvider client={queryClient}>
            <ApiProviders apis={apis}>
              <RouterProvider router={router} onError={routerErrorHandler(import.meta.env.PROD)} />
            </ApiProviders>
          </QueryClientProvider>
        </CryptoGate>
      </ErrorBoundary>
    </LanguageProvider>
  )
}
