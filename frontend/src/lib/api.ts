import type { ApiResponse } from '../types/api'

/**
 * Typed fetch wrapper for the backend API.
 *
 * In development the Vite proxy forwards /api and /actuator to the backend,
 * so BASE is empty and everything is same-origin. VITE_API_BASE_URL exists
 * for deployments where a proxy is not an option.
 */
const BASE: string = import.meta.env.VITE_API_BASE_URL ?? ''

/** Thrown for any non-2xx response, carrying what the UI needs to react. */
export class ApiError extends Error {
  readonly status: number
  /** Populated from the Retry-After header on 429 responses. */
  readonly retryAfterSeconds: number | null

  constructor(message: string, status: number, retryAfterSeconds: number | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.retryAfterSeconds = retryAfterSeconds
  }

  get isRateLimited(): boolean {
    return this.status === 429
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }
}

// Set by the auth layer (Phase 3); the client itself stays auth-agnostic.
let accessTokenProvider: () => string | null = () => null
let onUnauthorized: (() => Promise<boolean>) | null = null

export function configureAuth(
  tokenProvider: () => string | null,
  refreshHandler: (() => Promise<boolean>) | null,
): void {
  accessTokenProvider = tokenProvider
  onUnauthorized = refreshHandler
}

async function rawRequest(path: string, options: RequestInit): Promise<Response> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  const token = accessTokenProvider()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return fetch(`${BASE}${path}`, { ...options, headers })
}

async function toApiError(response: Response): Promise<ApiError> {
  let message = `Request failed (${response.status})`
  try {
    const body = (await response.json()) as ApiResponse<unknown>
    if (body?.message) message = body.message
  } catch {
    // Non-JSON body (proxy error page etc.) - keep the generic message.
  }
  const retryAfter = response.headers.get('Retry-After')
  return new ApiError(
    message,
    response.status,
    retryAfter ? Number.parseInt(retryAfter, 10) : null,
  )
}

/**
 * Perform a request and unwrap the ApiResponse envelope.
 *
 * On a 401 with a refresh handler configured, refreshes once and retries the
 * original request - the standard dance for a 15-minute access token.
 */
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response = await rawRequest(path, options)

  if (response.status === 401 && onUnauthorized) {
    const refreshed = await onUnauthorized()
    if (refreshed) {
      response = await rawRequest(path, options)
    }
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  const body = (await response.json()) as ApiResponse<T>
  if (!body.success) {
    throw new ApiError(body.message ?? 'Request failed', response.status)
  }
  return body.data as T
}

export function apiPost<T>(path: string, payload: unknown): Promise<T> {
  return apiFetch<T>(path, { method: 'POST', body: JSON.stringify(payload) })
}

export function apiGet<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'GET' })
}

export function apiDelete<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'DELETE' })
}

/** Backend liveness, used by the header badge. Actuator is not enveloped. */
export async function checkBackendHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${BASE}/actuator/health`)
    if (!response.ok) return false
    const body = (await response.json()) as { status?: string }
    return body.status === 'UP'
  } catch {
    return false
  }
}
