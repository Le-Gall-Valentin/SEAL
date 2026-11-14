import type { ReactNode } from 'react'
import { useMessages } from '@/shared/i18n'
import { passwordMessages } from '../i18n'
import type { PasswordRules, RuleState } from '../model/passwordPolicy'

const RULES = ['length', 'context', 'blocklist'] as const

function Rule({ state, children }: { readonly state: RuleState; readonly children: string }) {
  const met = state === 'met'
  return (
    <li
      data-state={met ? 'met' : 'unmet'}
      className="flex items-center gap-1.5 data-[state=met]:text-accent-text"
    >
      <svg
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        className="flex-none"
      >
        {/* ADR 0024, point 9 : une règle non respectée, ou non vérifiée, garde un cercle vide. */}
        {met ? <path d="M5 12.5l4.5 4.5L19 7.5" /> : <circle cx="12" cy="12" r="7" />}
      </svg>
      {children}
    </li>
  )
}

interface PasswordRulesListProps {
  readonly rules: PasswordRules
  /** Lignes ajoutées après les règles, propres à l'écran. */
  readonly children?: ReactNode
}

/** AUTH-11 : les trois règles en direct (Inscription-Telephone, Nouveau-Mot-de-passe). */
export function PasswordRulesList({ rules, children }: PasswordRulesListProps) {
  const t = useMessages(passwordMessages)
  return (
    <ul className="m-0 flex list-none flex-col gap-1 p-0 text-[13px] leading-[1.35] text-ink-3">
      {RULES.map((rule) => (
        <Rule key={rule} state={rules[rule]}>
          {t(rule)}
        </Rule>
      ))}
      {children}
    </ul>
  )
}

/** Inscription-Telephone : « Aucune règle de composition… », sans la phrase sur AUTH-12 (ADR 0007, point 9). */
export function CompositionNote() {
  const t = useMessages(passwordMessages)
  return <li className="pl-5">{t('composition')}</li>
}
