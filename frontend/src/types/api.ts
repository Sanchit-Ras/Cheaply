/**
 * TypeScript mirror of the backend contract.
 *
 * Field names follow the wire format exactly - including the snake_case
 * outliers (`price_per_unit`, `image_url`) the backend emits via
 * @JsonProperty for compatibility. Do not "fix" them here; they are the API.
 */

/** Envelope every backend endpoint wraps its payload in. */
export interface ApiResponse<T> {
  success: boolean
  message: string
  /** Absent on errors and on Void endpoints (NON_NULL serialisation). */
  data?: T
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

export type StoreOutcome = 'ok' | 'empty' | 'failed'

/** Per-store scrape outcome, so a broken store is never mistaken for "no results". */
export interface StoreStatus {
  name: string
  status: StoreOutcome
  count: number
  error: string | null
}

export type Unit = 'kg' | 'L'

export interface Product {
  title: string
  /** Price exactly as the store displayed it - for showing back to users. */
  price: string
  /** Parsed price, null when unreadable (never zero). */
  numericPrice: number | null
  weight: string | null
  normalizedWeight: number | null
  /** null when no quantity could be extracted - such products carry no rank. */
  unit: Unit | null
  price_per_unit: number | null
  /** 1-based position within this product's unit group; null if uncomparable. */
  rank: number | null
  /** True for the cheapest product in its unit group. */
  bestValue: boolean
  source: string
  link: string
  image_url: string
}

export interface SearchResponse {
  query: string
  totalResults: number
  /** True when served from Redis rather than a fresh scrape. */
  cached: boolean
  /** True when at least one store failed - the comparison is incomplete. */
  partial: boolean
  stores?: StoreStatus[]
  products: Product[]
  /** Only present for authenticated searches. */
  recentSearches?: string[]
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  userId: number
  username: string
  email: string
  role: string
}

export interface UserResponse {
  id: number
  username: string
  email: string
  role: string
  createdAt: string
}

// ---------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------

export interface SearchHistoryItem {
  id: number
  query: string
  searchedAt: string
}
