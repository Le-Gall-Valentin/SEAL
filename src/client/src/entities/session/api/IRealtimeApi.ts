/** CDC 12.2 : événements du serveur que le client suit ; `session.revoked` mène à la fin de la session. */
export type RealtimeEvent =
  | { readonly type: 'friend.request'; readonly requestId: string }
  | { readonly type: 'friend.update'; readonly userId: string }
  | { readonly type: 'conv.update'; readonly conversationId: string }

export interface RealtimeListener {
  readonly onEvent: (event: RealtimeEvent) => void
  /** TRN-15 : chaque (re)connexion, pour relire ce qui a pu être manqué. */
  readonly onOpen: () => void
  /** Session finie (4401, `session.revoked`) ou connexions refusées (4429). */
  readonly onEnd: (reason: 'session' | 'refused') => void
}

/** TRN-10 : la connexion temps réel de la session ; `connect` rend la fonction qui la ferme. */
export interface IRealtimeApi {
  connect(listener: RealtimeListener): () => void
}
