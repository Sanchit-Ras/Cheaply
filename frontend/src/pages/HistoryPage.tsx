import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ApiError, apiDelete, apiGet } from '../lib/api'
import type { SearchHistoryItem } from '../types/api'

function formatWhen(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function HistoryPage() {
  const { user, initialising } = useAuth()
  const [items, setItems] = useState<SearchHistoryItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [clearing, setClearing] = useState(false)

  useEffect(() => {
    if (!user) return
    apiGet<SearchHistoryItem[]>('/api/history')
      .then(setItems)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load history.'),
      )
  }, [user])

  const handleClear = async () => {
    if (!window.confirm('Delete your entire search history?')) return
    setClearing(true)
    try {
      await apiDelete('/api/history')
      setItems([])
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not clear history.')
    } finally {
      setClearing(false)
    }
  }

  if (initialising) return null

  if (!user) {
    return (
      <section className="rounded-xl bg-white p-8 text-center text-sm text-slate-500 shadow-sm ring-1 ring-slate-200">
        <p>
          <Link to="/login" className="font-medium text-brand-600 hover:underline">
            Log in
          </Link>{' '}
          to see your search history.
        </p>
      </section>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold">Search history</h1>
        {items && items.length > 0 && (
          <button
            onClick={handleClear}
            disabled={clearing}
            className="rounded-md border border-red-200 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            {clearing ? 'Clearing...' : 'Clear all'}
          </button>
        )}
      </div>

      {error && (
        <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {items === null && !error && (
        <p className="text-sm text-slate-500">Loading...</p>
      )}

      {items && items.length === 0 && (
        <section className="rounded-xl bg-white p-8 text-center text-sm text-slate-500 shadow-sm ring-1 ring-slate-200">
          Nothing here yet - your searches will appear once you run some.
        </section>
      )}

      {items && items.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          {items.map((item) => (
            <li key={item.id}>
              <Link
                to={`/?q=${encodeURIComponent(item.query)}`}
                className="flex items-center justify-between px-4 py-3 text-sm hover:bg-slate-50"
              >
                <span className="font-medium text-slate-800">{item.query}</span>
                <span className="text-xs text-slate-400">{formatWhen(item.searchedAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
