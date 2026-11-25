/** Port de `POST /api/v1/conversations/{id}/leave` (14.5, GRP-03). */
export interface ILeaveGroupApi {
  leave(conversationId: string): Promise<void>
}
