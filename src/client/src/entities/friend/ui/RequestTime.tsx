import { useMessages } from '@/shared/i18n'
import { useClock } from '@/shared/lib'
import { friendMessages } from '../i18n'
import { relativeAge } from '../model/friends'

interface RequestTimeProps {
  readonly variant: 'received' | 'sent'
  readonly date: Date
}

const DAY_MONTH = new Intl.DateTimeFormat('fr', { day: 'numeric', month: 'short' })

/** ADR 0038 (point 2) : « Reçue il y a 2 heures », « Envoyée hier »… sur l'horloge injectée. */
export function RequestTime({ variant, date }: RequestTimeProps) {
  const t = useMessages(friendMessages)
  const age = relativeAge(date, new Date(useClock().now()))
  const text = (() => {
    switch (age.unit) {
      case 'now':
        return t('now')
      case 'minutes':
        return age.count === 1 ? t('oneMinute') : t('minutes', { count: String(age.count) })
      case 'hours':
        return age.count === 1 ? t('oneHour') : t('hours', { count: String(age.count) })
      case 'yesterday':
        return t('yesterday')
      case 'days':
        return t('days', { count: String(age.count) })
      case 'date':
        return t('date', { date: DAY_MONTH.format(age.date) })
    }
  })()
  return (
    <time dateTime={date.toISOString()} className="text-[12px] text-ink-3">
      {t(variant, { age: text })}
    </time>
  )
}
