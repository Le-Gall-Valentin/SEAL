/**
 * ADR 0029, point 3 : exécute `task` sous le verrou `name`, partagé par les onglets de l'origine (Web Locks) ; une
 * seconde tâche du même verrou attend la fin de la première, même si elle échoue. Le verrou n'est pas réentrant :
 * une tâche ne le redemande pas.
 */
export async function withLock<T>(name: string, task: () => Promise<T>): Promise<T> {
  return navigator.locks.request(name, task)
}
