import { Component, type ReactNode } from 'react'
import { ErrorPage } from '../layouts'

interface ErrorBoundaryProps {
  readonly children: ReactNode
}

interface ErrorBoundaryState {
  readonly failed: boolean
}

/**
 * A10 : une erreur de rendu hors du routeur (fournisseurs, routeur lui-même) affiche l'état « Erreur de chargement »,
 * jamais la pile d'appels ; « Réessayer » refait le rendu des enfants. Les erreurs des routes passent par
 * RouteErrorBoundary, car React Router les capte avant toute frontière extérieure.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true }
  }

  private readonly retry = () => {
    this.setState({ failed: false })
  }

  override render() {
    return this.state.failed ? <ErrorPage onRetry={this.retry} /> : this.props.children
  }
}
