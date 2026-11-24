import type { FormFailure } from '@/shared/lib'

/**
 * FRD-01, ADR 0038 (point 8) : issue d'une invitation reçue acceptée depuis le formulaire, après un 409
 * INCOMING_REQUEST_EXISTS ; la feature qui répond aux invitations la produit, celle qui invite la lit.
 */
export type AcceptResult = { readonly kind: 'accepted' } | { readonly kind: 'gone' } | FormFailure
