/** Port de `DELETE /api/v1/conversations/{id}/members/{userId}` (14.5, GRP-03). */
export interface IRemoveMemberApi {
  exclude(conversationId: string, userId: string): Promise<void>
}
