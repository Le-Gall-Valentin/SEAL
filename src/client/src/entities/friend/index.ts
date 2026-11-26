// API publique de la tranche friend (ADR 0012) : listes d'amis et d'invitations (14.4, ADR 0037, ADR 0038).
export type { Friend, IFriendsApi, ReceivedRequest, SentRequest } from './api/IFriendsApi'
export { friendsApi, SENT_REQUEST_RESPONSE, sentRequestOf } from './api/friendsApi'
export type { AcceptResult } from './model/acceptResult'
export { FriendsApiProvider } from './model/friendsPort'
export { matchFriend, tintOf, usernameInitials } from './model/friends'
export {
  invalidateFriends,
  markFriendsStale,
  refetchFriends,
  useFriends,
  useReceivedRequests,
  useSentRequests,
} from './model/useFriendLists'
export { affinity, PICKER_SIZE, rankFriends } from './model/friendPicker'
export type { MatchRange, RankedFriend } from './model/friendPicker'
export { FriendPicker } from './ui/FriendPicker'
export { RequestTime } from './ui/RequestTime'
