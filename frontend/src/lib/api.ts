
import axios from 'axios'
import { useAuthStore } from '../store/authStore'
import type { AuthResponse, Connector, GraphData, QueryResponse, ApiKey } from '../types'

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export const api = axios.create({
  baseURL: BASE_URL,
  timeout: 60_000,
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  const orgId = useAuthStore.getState().organizationId
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  if (orgId) config.headers['X-Organization-Id'] = orgId
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      useAuthStore.getState().clearAuth()
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

// Auth
export const authApi = {
  register: (email: string, password: string, displayName: string, organizationName: string) =>
    api.post<AuthResponse>('/api/v1/auth/register', { email, password, displayName, organizationName }),

  login: (email: string, password: string) =>
    api.post<AuthResponse>('/api/v1/auth/login', { email, password }),

  logout: () => api.post<void>('/api/v1/auth/logout'),
}

// Query
export const queryApi = {
  ask: (question: string, maxResults = 8) =>
    api.post<QueryResponse>('/api/v1/query', { question, maxResults }),
}

// Graph
export const graphApi = {
  getPeople: () => api.get<GraphData>('/api/v1/graph/people'),
  explore: (query: string) => api.get<GraphData>('/api/v1/graph/explore', { params: { query } }),
}

// Connectors
export const connectorApi = {
  list: () => api.get<Connector[]>('/api/v1/connectors'),
  activate: (id: string) => api.post<Connector>(`/api/v1/connectors/${id}/activate`),
  deactivate: (id: string) => api.post<Connector>(`/api/v1/connectors/${id}/deactivate`),
}

// API Keys
export const apiKeyApi = {
  list: () => api.get<ApiKey[]>('/api/v1/users/me/api-keys'),
  create: (name: string) => api.post<ApiKey>('/api/v1/users/me/api-keys', { name }),
  delete: (id: string) => api.delete(`/api/v1/users/me/api-keys/${id}`),
}
