import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { checkBackendHealth } from '../lib/api'

const navClass = ({ isActive }: { isActive: boolean }) =>
  isActive
    ? 'rounded-md px-3 py-1.5 text-sm font-semibold text-brand-600'
    : 'rounded-md px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [backendUp, setBackendUp] = useState<boolean | null>(null)

  useEffect(() => {
    let cancelled = false
    const probe = () => {
      checkBackendHealth().then((up) => {
        if (!cancelled) setBackendUp(up)
      })
    }
    probe()
    const interval = setInterval(probe, 30_000)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [])

  const handleLogout = async () => {
    await logout()
    navigate('/')
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-2.5">
          <Link to="/" className="flex items-center gap-2.5">
            <img
              src="/logo.png"
              alt="Cheaply"
              className="h-9 w-auto rounded-md shadow-sm"
            />
            <span
              title={
                backendUp === null
                  ? 'Checking backend...'
                  : backendUp
                    ? 'All systems up'
                    : 'Backend is unreachable'
              }
              className={`mt-0.5 inline-block h-2 w-2 rounded-full ${
                backendUp === null
                  ? 'bg-slate-300'
                  : backendUp
                    ? 'bg-emerald-500'
                    : 'bg-red-500'
              }`}
            />
          </Link>

          <nav className="flex items-center gap-1">
            <NavLink to="/" className={navClass}>
              Search
            </NavLink>
            {user && (
              <NavLink to="/history" className={navClass}>
                History
              </NavLink>
            )}

            {user ? (
              <div className="ml-3 flex items-center gap-3">
                <span className="hidden text-sm text-slate-500 sm:inline">
                  Hi, <span className="font-semibold text-slate-800">{user.username}</span>
                </span>
                <button
                  onClick={handleLogout}
                  className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
                >
                  Log out
                </button>
              </div>
            ) : (
              <div className="ml-3 flex items-center gap-2">
                <Link
                  to="/login"
                  className="rounded-lg px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
                >
                  Log in
                </Link>
                <Link
                  to="/signup"
                  className="rounded-lg bg-brand-500 px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-600"
                >
                  Sign up
                </Link>
              </div>
            )}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-5xl px-4 py-6 text-center text-xs leading-relaxed text-slate-400">
          Prices are scraped live from Amazon, Flipkart, BigBasket and Blinkit,
          then normalised to a common unit so every pack size competes fairly.
          <br />
          Cold searches can take up to a minute; repeated searches are served
          from cache.
        </div>
      </footer>
    </div>
  )
}
