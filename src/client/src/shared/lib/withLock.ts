/**
 * ADR 0029, point 3 : exécute `task` sous le verrou `name`, partagé par les onglets de l'origine (Web Locks) ; une
 * seconde tâche du même verrou attend la fin de la première, même si elle échoue. Le verrou n'est pas réentrant :
 * une tâche ne le redemande pas. `onWait` est appelé si le verrou est déjà tenu et que la tâche doit l'attendre
 * (ADR 0029, point 13).
 */
export async function withLock<T>(
  name: string,
  task: () => Promise<T>,
  onWait?: () => void,
): Promise<T> {
  if (onWait === undefined) {
    return navigator.locks.request(name, task)
  }
  const immediate = await navigator.locks.request(name, { ifAvailable: true }, async (lock) =>
    lock === null ? undefined : { value: await task() },
  )
  if (immediate !== undefined) {
    return immediate.value
  }
  onWait()
  return navigator.locks.request(name, task)
}
