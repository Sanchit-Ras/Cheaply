import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { apiGet, apiPost } from '../lib/api'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  initAuth,
  storeTokens,
} from '../lib/auth'
import type { AuthResponse, UserResponse } from '../types/api'

interface AuthContextValue {
  user: UserResponse | null
  /** True while the stored session is being validated on first load. */
  initialising: boolean
  login: (username: string, password: string) => Promise<void>
  signup: (username: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function userFromAuth(auth: AuthResponse): UserResponse {
  return {
    id: auth.userId,
    username: auth.username,
    email: auth.email,
    role: auth.role,
    createdAt: '',
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [initialising, setInitialising] = useState(true)

  // Restore the session on first load: if tokens exist, ask the backend who
  // we are. An expired access token is refreshed transparently by the client.
  useEffect(() => {
    initAuth()

    const onLoggedOut = () => setUser(null)
    window.addEventListener('cheaply:logged-out', onLoggedOut)

    if (getAccessToken()) {
      apiGet<UserResponse>('/api/auth/me')
        .then(setUser)
        .catch(() => clearTokens())
        .finally(() => setInitialising(false))
    } else {
      setInitialising(false)
    }

    return () => window.removeEventListener('cheaply:logged-out', onLoggedOut)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const auth = await apiPost<AuthResponse>('/api/auth/login', { username, password })
    storeTokens(auth)
    setUser(userFromAuth(auth))
  }, [])

  const signup = useCallback(
    async (username: string, email: string, password: string) => {
      const auth = await apiPost<AuthResponse>('/api/auth/signup', {
        username,
        email,
        password,
      })
      storeTokens(auth)
      setUser(userFromAuth(auth))
    },
    [],
  )

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      // Revoke server-side; a failure here changes nothing for the user.
      try {
        await apiPost('/api/auth/logout', { refreshToken })
      } catch {
        // Token already invalid - the desired end state is already true.
      }
    }
    clearTokens()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, initialising, login, signup, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
