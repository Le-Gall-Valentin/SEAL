/**
 * Gestes qui comptent comme une action de l'utilisateur, écoutés en capture. Le défilement se compte par ses gestes
 * (molette, toucher, clavier, pointeur) et non par `scroll`, que la page émet aussi seule : un élément ajouté en tête
 * d'une liste défilée fait recaler le défilement sans personne devant l'écran (AUTH-52, audit F1). Revenir sur
 * l'onglet compte aussi (`focus`, `visibilitychange` vers visible, ADR 0044).
 */
const ACTIVITY_EVENTS = [
  'pointerdown',
  'pointermove',
  'keydown',
  'wheel',
  'touchstart',
  'focus',
] as const

/** AUTH-52 : une action prolonge la session au plus une fois par minute ; la garde suit la même fenêtre. */
const RECENT_MS = 60_000

/** ADR 0044 : une rafale d'événements ne lance qu'une tâche par clé, après ce délai. */
export const COALESCE_MS = 250

export interface ActivityGate {
  /**
   * Lance `task` après un court délai de regroupement si l'utilisateur a agi depuis moins d'une minute, page
   * visible ; sinon à sa prochaine action ou à son retour sur l'onglet. Une tâche en attente de même clé est remplacée.
   */
  run(key: string, task: () => void): void
  dispose(): void
}

interface ActivityGateOptions {
  readonly now?: () => number
  readonly visible?: () => boolean
}

/**
 * AUTH-52, ADR 0043 (point 4), ADR 0044 (décisions 1 et 2) : une requête née d'un événement du serveur, ou la
 * réouverture d'une connexion, ne part qu'après une action récente de l'utilisateur, pour qu'une page laissée ouverte
 * expire même quand ses amis agissent.
 */
export function createActivityGate({
  now = () => Date.now(),
  visible = () => document.visibilityState !== 'hidden',
}: ActivityGateOptions = {}): ActivityGate {
  const pending = new Map<string, () => void>()
  let lastAction = Number.NEGATIVE_INFINITY
  let timer: ReturnType<typeof setTimeout> | null = null

  const flush = () => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
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
  const shown = () => {
    if (visible()) {
      acted()
    }
  }
  const options = { capture: true, passive: true }
  for (const type of ACTIVITY_EVENTS) {
    window.addEventListener(type, acted, options)
  }
  document.addEventListener('visibilitychange', shown)

  return {
    run(key, task) {
      pending.set(key, task)
      if (timer === null && visible() && now() - lastAction < RECENT_MS) {
        timer = setTimeout(flush, COALESCE_MS)
      }
    },
    dispose() {
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
      pending.clear()
      for (const type of ACTIVITY_EVENTS) {
        window.removeEventListener(type, acted, options)
      }
      document.removeEventListener('visibilitychange', shown)
    },
  }
}
