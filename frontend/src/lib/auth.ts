import type { ApiResponse, AuthResponse } from '../types/api'
import { configureAuth } from './api'

/**
 * Token storage and session refresh.
 *
 * Tokens live in localStorage. That is a deliberate, documented trade-off:
 * it is vulnerable to XSS in a way an httpOnly cookie is not, but it keeps
 * the backend fully stateless and CSRF-free. For this project's threat model
 * the simplicity wins; the README says so out loud.
 */

const ACCESS_KEY = 'cheaply.accessToken'
const REFRESH_KEY = 'cheaply.refreshToken'

// Same computation as api.ts - the refresh call must NOT go through apiFetch,
// or a 401 during refresh would recurse into another refresh.
const BASE: string = import.meta.env.VITE_API_BASE_URL ?? ''

export function getAccessToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_KEY)
  } catch {
    return null
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

export function storeTokens(auth: AuthResponse): void {
  try {
    localStorage.setItem(ACCESS_KEY, auth.accessToken)
    localStorage.setItem(REFRESH_KEY, auth.refreshToken)
  } catch {
    // Storage unavailable (private mode etc.) - the session just won't persist.
  }
}

export function clearTokens(): void {
  try {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  } catch {
    // Nothing to clear.
  }
  window.dispatchEvent(new Event('cheaply:logged-out'))
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false

  try {
    const response = await fetch(`${BASE}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!response.ok) {
      // Expired, revoked, or already rotated away - the session is over.
      clearTokens()
      return false
    }
    const body = (await response.json()) as ApiResponse<AuthResponse>
    if (!body.data) return false
    storeTokens(body.data)
    return true
  } catch {
    // Network failure: keep the tokens; a later request can try again.
    return false
  }
}

let refreshInFlight: Promise<boolean> | null = null

/**
 * Refresh the session, deduplicated.
 *
 * Refresh tokens are SINGLE-USE on the backend (rotation): if two requests
 * hit 401 together and each ran its own refresh, the second would present an
 * already-rotated token and log the user out. All concurrent callers must
 * therefore share one in-flight refresh promise.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

/** Wire the API client to this token store. Call once at startup. */
export function initAuth(): void {
  configureAuth(getAccessToken, refreshSession)
}
