import { useEffect, useId, useRef } from 'react'
import type { IncomingAcceptResult } from './AddFriendForm'
import { useMessages } from '@/shared/i18n'
import { sendFriendRequestMessages } from '../i18n'
import { AddFriendForm } from './AddFriendForm'
import { CloseIcon } from './icons'

interface AddFriendSheetProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly onAccept: (requestId: string) => Promise<IncomingAcceptResult>
}

/**
 * Feuille « Ajouter un ami » d'Amis-Telephone, ancrée en bas ; sur tablette, à la largeur du contenu (ADR 0038,
 * point 4). Modale native : focus piégé, Échap ferme.
 */
export function AddFriendSheet({ open, onClose, onAccept }: AddFriendSheetProps) {
  const t = useMessages(sendFriendRequestMessages)
  const titleId = useId()
  const ref = useRef<HTMLDialogElement>(null)
  const field = useRef<HTMLInputElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (dialog === null) {
      return
    }
    if (open && !dialog.open) {
      dialog.showModal()
      // showModal place le focus sur « Fermer » ; le champ le prend (comme ModalDialog).
      field.current?.focus()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      className="mx-auto mt-auto mb-0 w-full max-w-184 rounded-t-[18px] bg-raised p-0 pb-5 font-sans text-ink shadow-dialog backdrop:bg-overlay"
    >
      {open && (
        <>
          <div className="flex items-start pt-5 pr-3 pb-3 pl-6">
            <h2
              id={titleId}
              className="m-0 flex-1 pt-2 text-[20px] leading-[1.2] font-bold tracking-[-0.015em]"
            >
              {t('title')}
            </h2>
            <button
              type="button"
              aria-label={t('close')}
              onClick={onClose}
              className="grid size-11 place-items-center rounded-[10px] text-ink-2 hover:bg-sunken"
            >
              <CloseIcon />
            </button>
          </div>
          <div className="px-6">
            <AddFriendForm
              onAccept={onAccept}
              onAccepted={onClose}
              variant="sheet"
              inputRef={field}
            />
          </div>
        </>
      )}
    </dialog>
  )
}
