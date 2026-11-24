// API publique de la tranche friend (ADR 0012) : listes d'amis et d'invitations (14.4, ADR 0037, ADR 0038).
export type { Friend, IFriendsApi, Page, ReceivedRequest, SentRequest } from './api/IFriendsApi'
export { friendsApi, SENT_REQUEST_RESPONSE, sentRequestOf } from './api/friendsApi'
export { FriendsApiProvider } from './model/friendsPort'
export { matchFriend, normalizeSearch, tintOf, usernameInitials } from './model/friends'
export {
  FRIENDS_KEY,
  FRIENDS_ROOT_KEY,
  invalidateFriends,
  RECEIVED_REQUESTS_KEY,
  SENT_REQUESTS_KEY,
  useFriends,
  useReceivedRequests,
  useReceivedRequestsRefresh,
  useSentRequests,
} from './model/useFriendLists'
export { RequestTime } from './ui/RequestTime'
