import type { RealtimeEnd, RealtimeEvent } from '@/shared/ws'

/** CDC 12.2 : événements du serveur que le client suit ; `session.revoked` mène à la fin de la session. */
export type { RealtimeEvent }

export interface RealtimeListener {
  readonly onEvent: (event: RealtimeEvent) => void
  /** TRN-15 : chaque (re)connexion confirmée par le serveur, pour relire ce qui a pu être manqué. */
  readonly onOpen: () => void
  /** Session finie (4401, `session.revoked`) ou connexions refusées (4429). */
  readonly onEnd: (reason: RealtimeEnd) => void
  /** ADR 0044 (décision 2) : décide quand rouvrir une connexion tombée ; `resume` rouvre. */
  readonly beforeReconnect: (resume: () => void) => void
  /** ADR 0044 (décision 2) : trois échecs de suite sans réponse ; la session a peut-être fini. */
  readonly onUnreachable: () => void
}

/** TRN-10 : la connexion temps réel de la session ; `connect` rend la fonction qui la ferme. */
export interface IRealtimeApi {
  connect(listener: RealtimeListener): () => void
}
