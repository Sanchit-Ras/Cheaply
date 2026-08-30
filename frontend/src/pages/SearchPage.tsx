import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import ProductCard from '../components/ProductCard'
import { useAuth } from '../context/AuthContext'
import { ApiError, apiPost } from '../lib/api'
import type { Product, SearchResponse, Unit } from '../types/api'

/**
 * Staged loading copy. A cold search legitimately takes up to ~45 seconds
 * while four stores are scraped, and a frozen spinner for that long reads
 * as "broken". The messages advance on a timer to show life.
 */
const LOADING_STAGES = [
  { after: 0, text: 'Checking Amazon, Flipkart, BigBasket and Blinkit...' },
  { after: 8, text: 'The stores are responding - collecting products...' },
  { after: 20, text: 'Normalising prices to per-kg and per-litre...' },
  { after: 32, text: 'Almost there - cold searches can take up to a minute...' },
]

const EXAMPLE_QUERIES = ['tata salt', 'basmati rice 5kg', 'sunflower oil 1 litre', 'amul butter']

const UNIT_SECTIONS: { unit: Unit; heading: string }[] = [
  { unit: 'kg', heading: 'Priced per kilogram' },
  { unit: 'L', heading: 'Priced per litre' },
]

export default function SearchPage() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('q') ?? '')
  const [result, setResult] = useState<SearchResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadingStage, setLoadingStage] = useState(0)
  const [retryIn, setRetryIn] = useState(0)
  const requestSeq = useRef(0)

  // Advance the loading copy while a search is in flight.
  useEffect(() => {
    if (!loading) return
    setLoadingStage(0)
    const started = Date.now()
    const timer = setInterval(() => {
      const elapsed = (Date.now() - started) / 1000
      let stage = 0
      for (let i = 0; i < LOADING_STAGES.length; i++) {
        if (elapsed >= LOADING_STAGES[i].after) stage = i
      }
      setLoadingStage(stage)
    }, 1000)
    return () => clearInterval(timer)
  }, [loading])

  // Count down the rate-limit lockout.
  const rateLimited = retryIn > 0
  useEffect(() => {
    if (!rateLimited) return
    const timer = setInterval(() => setRetryIn((s) => Math.max(0, s - 1)), 1000)
    return () => clearInterval(timer)
  }, [rateLimited])

  const runSearch = useCallback(
    async (rawQuery: string) => {
      const cleaned = rawQuery.replace(/ +/g, ' ').trim()
      if (!cleaned || retryIn > 0) return

      const seq = ++requestSeq.current
      setQuery(cleaned)
      setLoading(true)
      setError(null)
      setResult(null)
      setSearchParams({ q: cleaned })

      try {
        const response = await apiPost<SearchResponse>('/api/search', { query: cleaned })
        if (seq === requestSeq.current) setResult(response)
      } catch (err) {
        if (seq !== requestSeq.current) return
        if (err instanceof ApiError && err.isRateLimited) {
          const wait = err.retryAfterSeconds ?? 30
          setRetryIn(wait)
          setError(`You are searching a little too fast - try again in ${wait}s.`)
        } else if (err instanceof ApiError) {
          setError(err.message)
        } else {
          setError('Could not reach the server. Is the backend running?')
        }
      } finally {
        if (seq === requestSeq.current) setLoading(false)
      }
    },
    [retryIn, setSearchParams],
  )

  // Auto-run when arriving with ?q= (e.g. from the history page).
  const initialQuery = useRef(searchParams.get('q'))
  useEffect(() => {
    if (initialQuery.current) runSearch(initialQuery.current)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const failedStores = result?.stores?.filter((s) => s.status === 'failed') ?? []
  const unranked: Product[] = result?.products.filter((p) => p.unit === null) ?? []
  const pristine = !result && !loading && !error

  return (
    <div className="space-y-6">
      <section
        className={`rounded-2xl bg-white shadow-sm ring-1 ring-slate-200 ${
          pristine ? 'px-6 py-12 text-center sm:py-16' : 'p-6'
        }`}
      >
        {pristine && (
          <>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 sm:text-3xl">
              Find the <span className="text-brand-500">real</span> cheapest grocery
            </h1>
            <p className="mx-auto mt-2 max-w-md text-sm text-slate-500">
              We compare price per kilogram and per litre across four stores, so a
              500&nbsp;g pack competes fairly with a 5&nbsp;kg one.
            </p>
          </>
        )}

        <form
          className={`flex gap-2 ${pristine ? 'mx-auto mt-6 max-w-xl' : ''}`}
          onSubmit={(e) => {
            e.preventDefault()
            runSearch(query)
          }}
        >
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            maxLength={120}
            placeholder="Search groceries, e.g. tata salt"
            className="w-full rounded-xl border border-slate-300 px-4 py-3 text-sm shadow-sm outline-none focus:border-brand-400 focus:ring-4 focus:ring-brand-100"
          />
          <button
            type="submit"
            disabled={loading || retryIn > 0 || !query.trim()}
            className="shrink-0 rounded-xl bg-brand-500 px-6 py-3 text-sm font-semibold text-white shadow-sm hover:bg-brand-600 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {retryIn > 0 ? `Wait ${retryIn}s` : loading ? 'Searching...' : 'Search'}
          </button>
        </form>

        {pristine && (
          <div className="mt-5 flex flex-wrap items-center justify-center gap-2 text-xs">
            <span className="text-slate-400">Try:</span>
            {EXAMPLE_QUERIES.map((example) => (
              <button
                key={example}
                onClick={() => runSearch(example)}
                className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-slate-600 hover:border-brand-200 hover:bg-brand-50 hover:text-brand-600"
              >
                {example}
              </button>
            ))}
          </div>
        )}

        {result?.recentSearches && result.recentSearches.length > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
            <span className="text-slate-400">Recent:</span>
            {result.recentSearches.map((recent) => (
              <button
                key={recent}
                onClick={() => runSearch(recent)}
                className="rounded-full bg-slate-100 px-3 py-1 text-slate-600 hover:bg-slate-200"
              >
                {recent}
              </button>
            ))}
          </div>
        )}
      </section>

      {loading && (
        <section className="space-y-4">
          <div className="flex items-center gap-3 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
            <div className="h-5 w-5 shrink-0 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
            <p className="text-sm text-slate-600">{LOADING_STAGES[loadingStage].text}</p>
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="flex animate-pulse gap-3 rounded-xl bg-white p-3 shadow-sm ring-1 ring-slate-200"
              >
                <div className="h-20 w-20 shrink-0 rounded-lg bg-slate-100" />
                <div className="flex-1 space-y-2 py-1">
                  <div className="h-3 w-4/5 rounded bg-slate-100" />
                  <div className="h-3 w-3/5 rounded bg-slate-100" />
                  <div className="h-4 w-2/5 rounded bg-slate-100" />
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {error && !loading && (
        <section className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </section>
      )}

      {result && !loading && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
            <span>
              {result.totalResults} result{result.totalResults === 1 ? '' : 's'} for{' '}
              <span className="font-semibold text-slate-700">"{result.query}"</span>
            </span>
            {result.cached && (
              <span className="rounded-full bg-slate-100 px-2 py-0.5">served from cache</span>
            )}
            {!user && (
              <span className="rounded-full bg-slate-100 px-2 py-0.5">
                log in to keep search history
              </span>
            )}
          </div>

          {result.partial && failedStores.length > 0 && (
            <section className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
              <span className="font-semibold">Partial results:</span>{' '}
              {failedStores.map((s) => s.name).join(' and ')} could not be reached, so
              some prices may be missing from this comparison.
            </section>
          )}

          {result.totalResults === 0 && (
            <section className="rounded-xl bg-white p-10 text-center shadow-sm ring-1 ring-slate-200">
              <p className="text-3xl">&#128269;</p>
              <p className="mt-2 text-sm font-medium text-slate-700">No products found</p>
              <p className="mt-1 text-sm text-slate-500">
                Try a simpler term, like a brand or item name.
              </p>
            </section>
          )}

          {UNIT_SECTIONS.map(({ unit, heading }) => {
            const group = result.products.filter((p) => p.unit === unit)
            if (group.length === 0) return null
            return (
              <section key={unit}>
                <div className="mb-3 flex items-baseline gap-2">
                  <h2 className="text-sm font-bold text-slate-800">{heading}</h2>
                  <span className="text-xs text-slate-400">
                    {group.length} item{group.length === 1 ? '' : 's'}, cheapest first
                  </span>
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {group.map((product, i) => (
                    <ProductCard key={`${unit}-${i}`} product={product} />
                  ))}
                </div>
              </section>
            )
          })}

          {unranked.length > 0 && (
            <section>
              <div className="mb-1 flex items-baseline gap-2">
                <h2 className="text-sm font-bold text-slate-800">Other matches</h2>
                <span className="text-xs text-slate-400">not ranked</span>
              </div>
              <p className="mb-3 text-xs text-slate-400">
                No pack size could be read from these listings, so they are not ranked.
              </p>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {unranked.map((product, i) => (
                  <ProductCard key={`unranked-${i}`} product={product} />
                ))}
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  )
}
