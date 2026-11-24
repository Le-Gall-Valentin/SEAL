import { PersonGlyph } from '@/shared/ui'

// Icônes de la navigation principale, reprises de la maquette (22 px, trait de 1,7) ; décoratives.

const STROKE = {
  width: 22,
  height: 22,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

export function ConversationsIcon() {
  return (
    <svg {...STROKE}>
      <path d="M5 4.5h14A2.5 2.5 0 0 1 21.5 7v8a2.5 2.5 0 0 1-2.5 2.5h-7.2L7 21v-3.5H5A2.5 2.5 0 0 1 2.5 15V7A2.5 2.5 0 0 1 5 4.5z" />
    </svg>
  )
}

export function SettingsIcon() {
  return (
    <svg {...STROKE}>
      <path d="M4 7h9.5M18.5 7H20M4 17h1.5M10.5 17H20" />
      <circle cx="16" cy="7" r="2.3" />
      <circle cx="8" cy="17" r="2.3" />
    </svg>
  )
}

export function FriendsIcon() {
  return (
    <svg {...STROKE}>
      <PersonGlyph />
      <path d="M15.5 4.6a3.4 3.4 0 0 1 0 6.4" />
      <path d="M17.6 14.7c2.1.7 3.5 2.6 3.9 5.3" />
    </svg>
  )
}
