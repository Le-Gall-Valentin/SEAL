/**
 * Gestes qui comptent comme une action de l'utilisateur, écoutés en capture. Le défilement se compte par ses gestes
 * (molette, toucher, clavier, pointeur) et non par `scroll`, que la page émet aussi seule : un élément ajouté en tête
 * d'une liste défilée fait recaler le défilement sans personne devant l'écran (AUTH-52, audit F1).
 */
const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart'] as const

/** AUTH-52 : une action prolonge la session au plus une fois par minute ; la garde suit la même fenêtre. */
const RECENT_MS = 60_000

export interface ActivityGate {
  /** Lance `task` aussitôt si l'utilisateur a agi depuis moins d'une minute, page visible ; sinon à sa prochaine action. */
  run(key: string, task: () => void): void
  dispose(): void
}

interface ActivityGateOptions {
  readonly now?: () => number
  readonly visible?: () => boolean
}

/**
 * AUTH-52, ADR 0043 (point 4) : une requête née d'un événement du serveur ne part qu'après une action récente de
 * l'utilisateur, pour qu'une page laissée ouverte expire même quand ses amis agissent. Les tâches d'une même clé
 * en attente se remplacent : la dernière seule part.
 */
export function createActivityGate({
  now = () => Date.now(),
  visible = () => document.visibilityState !== 'hidden',
}: ActivityGateOptions = {}): ActivityGate {
  const pending = new Map<string, () => void>()
  let lastAction = Number.NEGATIVE_INFINITY

  const flush = () => {
    const tasks = [...pending.values()]
    pending.clear()
    tasks.forEach((task) => {
      task()
    })
  }
  const acted = () => {
    lastAction = now()
    if (pending.size > 0 && visible()) {
      flush()
    }
  }
  const options = { capture: true, passive: true }
  for (const type of ACTIVITY_EVENTS) {
    window.addEventListener(type, acted, options)
  }

  return {
    run(key, task) {
      pending.set(key, task)
      if (visible() && now() - lastAction < RECENT_MS) {
        flush()
      }
    },
    dispose() {
      pending.clear()
      for (const type of ACTIVITY_EVENTS) {
        window.removeEventListener(type, acted, options)
      }
    },
  }
}
