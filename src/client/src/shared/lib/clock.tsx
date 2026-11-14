import { createContext, type ReactNode, useContext } from 'react'

/** Horloge injectée (CLAUDE.md : dépendances et horloge injectées), en millisecondes Unix. */
export interface Clock {
  now(): number
}

const SYSTEM_CLOCK: Clock = { now: () => Date.now() }

const ClockContext = createContext<Clock>(SYSTEM_CLOCK)

/** Remplace l'horloge du système, dans les tests notamment. */
export function ClockProvider({
  clock,
  children,
}: {
  readonly clock: Clock
  readonly children: ReactNode
}) {
  return <ClockContext value={clock}>{children}</ClockContext>
}

/** L'horloge fournie, ou celle du système. */
export function useClock(): Clock {
  return useContext(ClockContext)
}
