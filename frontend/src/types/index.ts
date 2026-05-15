
export type UserRole = 'ADMIN' | 'MEMBER' | 'VIEWER'

export interface User {
  id: string
  email: string
  displayName: string | null
  role: UserRole
  isActive: boolean
  organizationId: string
  organizationName: string
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userId: string
  email: string
  displayName: string | null
  organizationId: string
  organizationName: string
  role: UserRole
}

export interface SourceDocument {
  sourceId: string
  sourceType: string
  url: string | null
  title: string | null
  score: number
}

export interface QueryResponse {
  answer: string
  sources: SourceDocument[]
  concepts: string[]
  people: string[]
  cacheHit: boolean
  durationMs: number
}

export interface Connector {
  id: string
  name: string
  sourceType: string
  status: 'ACTIVE' | 'INACTIVE' | 'ERROR'
  organizationId: string
  createdAt: string
  updatedAt: string
}

export interface ApiKey {
  id: string
  name: string
  keyPrefix: string
  plainKey?: string
  lastUsedAt: string | null
  expiresAt: string | null
  createdAt: string
}

export interface GraphNode {
  id: string
  label: string
  type: 'Person' | 'Document' | 'Concept' | 'Decision'
  properties: Record<string, unknown>
}

export interface GraphEdge {
  source: string
  target: string
  type: string
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}
