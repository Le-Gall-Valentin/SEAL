/** Port des transitions d'une invitation (10.1) : 204, ou 404 pour une invitation inconnue, d'un autre ou close. */
export interface IAnswerFriendRequestApi {
  accept(requestId: string): Promise<void>
  decline(requestId: string): Promise<void>
  revoke(requestId: string): Promise<void>
}
