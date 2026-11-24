/** Port de DELETE /api/v1/friends/{userId} (FRD-11) : 204, ou 404 hors amitié. */
export interface IRemoveFriendApi {
  unfriend(userId: string): Promise<void>
}
