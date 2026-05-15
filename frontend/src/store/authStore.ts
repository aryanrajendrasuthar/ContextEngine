
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse, UserRole } from '../types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  userId: string | null
  email: string | null
  displayName: string | null
  organizationId: string | null
  organizationName: string | null
  role: UserRole | null
  setAuth: (response: AuthResponse) => void
  clearAuth: () => void
  isAuthenticated: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      email: null,
      displayName: null,
      organizationId: null,
      organizationName: null,
      role: null,

      setAuth: (response: AuthResponse) =>
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          userId: response.userId,
          email: response.email,
          displayName: response.displayName,
          organizationId: response.organizationId,
          organizationName: response.organizationName,
          role: response.role,
        }),

      clearAuth: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userId: null,
          email: null,
          displayName: null,
          organizationId: null,
          organizationName: null,
          role: null,
        }),

      isAuthenticated: () => get().accessToken !== null,
    }),
    { name: 'contextengine-auth' }
  )
)
