import { type ReactNode, useEffect, useRef } from 'react'

/**
 * Tiroir de Conv-Tablette : `dialog` natif modal, 380 px à droite, voile --overlay ; feuille plein écran sur téléphone
 * (ADR 0041, point 3).
 */
export function Drawer({
  open,
  onClose,
  titleId,
  children,
}: {
  readonly open: boolean
  readonly onClose: () => void
  readonly titleId: string
  readonly children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const drawer = ref.current
    if (drawer === null) {
      return
    }
    if (open && !drawer.open) {
      drawer.showModal()
    } else if (!open && drawer.open) {
      drawer.close()
    }
  }, [open])
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        // Seul Échap adressé au tiroir le ferme, pas celui d'un dialogue ouvert depuis lui.
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
      className="ml-auto h-full max-h-none w-full max-w-full flex-col bg-raised p-0 text-ink shadow-dialog backdrop:bg-overlay open:flex sm:max-w-95"
    >
      {open && children}
    </dialog>
  )
}
