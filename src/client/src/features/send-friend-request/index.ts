// API publique de la tranche send-friend-request (ADR 0012) : inviter par identifiant (FRD-01 à FRD-03, ADR 0038).
export type { ISendFriendRequestApi } from './api/ISendFriendRequestApi'
export { sendFriendRequestApi } from './api/sendFriendRequestApi'
export { SendFriendRequestApiProvider } from './model/sendFriendRequestPort'
export { AddFriendForm, type IncomingAcceptResult } from './ui/AddFriendForm'
export { AddFriendSheet } from './ui/AddFriendSheet'
export { PersonPlusIcon } from './ui/icons'
