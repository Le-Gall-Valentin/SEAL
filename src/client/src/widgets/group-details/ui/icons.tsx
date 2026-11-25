import type { ReactNode } from 'react'

/** Pictogrammes des infos du groupe (Conv-Ordinateur, Conv-Tablette), décoratifs, aux tracés de la maquette. */
function Glyph({
  size,
  strokeWidth = 1.7,
  children,
}: {
  readonly size: number
  readonly strokeWidth?: number
  readonly children: ReactNode
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="flex-none"
    >
      {children}
    </svg>
  )
}

export function PencilIcon() {
  return (
    <Glyph size={16}>
      <path d="M14.5 5.5l4 4M4 20l1-4.5L15.8 4.7a1.8 1.8 0 0 1 2.5 0l1 1a1.8 1.8 0 0 1 0 2.5L8.5 19z" />
    </Glyph>
  )
}

export function ShieldIcon() {
  return (
    <Glyph size={18}>
      <path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z" />
      <path d="M8.8 12.2l2.2 2.2 4.3-4.6" />
    </Glyph>
  )
}

const PERSON = (
  <>
    <circle cx="10" cy="8" r="3.6" />
    <path d="M3 20.5c.8-3.7 3.6-5.8 7-5.8 1.4 0 2.7.3 3.8 1" />
  </>
)

export function PersonPlusIcon() {
  return (
    <Glyph size={16}>
      {PERSON}
      <path d="M18.5 13v6M15.5 16h6" />
    </Glyph>
  )
}

export function PersonMinusIcon() {
  return (
    <Glyph size={18}>
      {PERSON}
      <path d="M15.5 16h6" />
    </Glyph>
  )
}

export function LeaveIcon() {
  return (
    <Glyph size={18}>
      <path d="M14 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H14M10 12h10M16.5 8.5L20 12l-3.5 3.5" />
    </Glyph>
  )
}
