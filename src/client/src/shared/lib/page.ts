/** ADR 0037, point 3 : une page et le curseur de la suivante, `null` sur la dernière. */
export interface Page<T> {
  readonly items: readonly T[]
  readonly next: string | null
}
