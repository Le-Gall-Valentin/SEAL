import { useMessages } from '@/shared/i18n'
import { logoutMessages } from '../i18n'
import { useLogout } from '../model/useLogout'

/** AUTH-53 : bouton « Se déconnecter » des écrans Paramètres. */
export function LogoutButton() {
  const t = useMessages(logoutMessages)
  const logout = useLogout()
  return (
    <button
      type="button"
      disabled={logout.isPending}
      className="flex h-11 w-full items-center justify-center gap-2 rounded-[10px] bg-transparent font-sans text-[14.5px] font-semibold text-ink-2 disabled:opacity-55"
      onClick={() => {
        logout.mutate()
      }}
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M14 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H14M10 12h10M16.5 8.5L20 12l-3.5 3.5" />
      </svg>
      {t('logout')}
    </button>
  )
}
