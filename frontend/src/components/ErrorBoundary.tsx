import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/**
 * Last-resort guard: a rendering crash anywhere below shows a recoverable
 * message instead of a blank page.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled render error:', error, info.componentStack)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
          <div className="max-w-sm rounded-xl bg-white p-6 text-center shadow-sm ring-1 ring-slate-200">
            <p className="text-lg font-semibold text-slate-800">Something went wrong</p>
            <p className="mt-2 text-sm text-slate-500">
              An unexpected error occurred while rendering the page.
            </p>
            <button
              onClick={() => window.location.assign('/')}
              className="mt-4 rounded-lg bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600"
            >
              Back to search
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
