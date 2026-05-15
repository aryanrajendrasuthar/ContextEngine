
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiKeyApi } from '../lib/api'
import type { ApiKey } from '../types'

export default function SettingsPage() {
  const { email, displayName, organizationName, role } = useAuthStore()

  return (
    <div className="p-8 max-w-2xl space-y-10">
      <div>
        <h2 className="text-xl font-semibold text-gray-900">Settings</h2>
        <p className="text-sm text-gray-500 mt-1">Organization and account configuration</p>
      </div>

      <section>
        <h3 className="text-sm font-medium text-gray-900 mb-4">Account</h3>
        <div className="bg-white border border-gray-200 rounded-xl divide-y divide-gray-100">
          <InfoRow label="Email" value={email ?? '—'} />
          <InfoRow label="Display name" value={displayName ?? '—'} />
          <InfoRow label="Organization" value={organizationName ?? '—'} />
          <InfoRow label="Role" value={role ?? '—'} />
        </div>
      </section>

      <ApiKeysSection />
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-4 py-3">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="text-sm text-gray-900 font-medium">{value}</p>
    </div>
  )
}

function ApiKeysSection() {
  const queryClient = useQueryClient()
  const [newKeyName, setNewKeyName] = useState('')
  const [createdKey, setCreatedKey] = useState<string | null>(null)

  const { data: keys, isLoading } = useQuery({
    queryKey: ['api-keys'],
    queryFn: () => apiKeyApi.list().then((r) => r.data),
    retry: false,
  })

  const createMutation = useMutation({
    mutationFn: (name: string) => apiKeyApi.create(name).then((r) => r.data),
    onSuccess: (data: ApiKey) => {
      setCreatedKey(data.plainKey ?? null)
      setNewKeyName('')
      queryClient.invalidateQueries({ queryKey: ['api-keys'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiKeyApi.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['api-keys'] }),
  })

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    const name = newKeyName.trim()
    if (name) createMutation.mutate(name)
  }

  return (
    <section>
      <h3 className="text-sm font-medium text-gray-900 mb-1">API keys</h3>
      <p className="text-xs text-gray-500 mb-4">
        Use API keys to authenticate programmatic access to ContextEngine.
        The full key is only shown once at creation time.
      </p>

      {createdKey && (
        <div className="mb-4 bg-green-50 border border-green-200 rounded-xl px-4 py-3">
          <p className="text-xs font-medium text-green-800 mb-1">New API key created — copy it now:</p>
          <code className="text-xs text-green-900 font-mono break-all">{createdKey}</code>
          <button
            onClick={() => setCreatedKey(null)}
            className="mt-2 block text-xs text-green-700 hover:underline"
          >
            Done
          </button>
        </div>
      )}

      <form onSubmit={handleCreate} className="flex gap-2 mb-4">
        <input
          type="text"
          value={newKeyName}
          onChange={(e) => setNewKeyName(e.target.value)}
          placeholder="Key name (e.g. CI pipeline)"
          className="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent"
        />
        <button
          type="submit"
          disabled={!newKeyName.trim() || createMutation.isPending}
          className="px-4 py-2 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-800 disabled:opacity-40 transition-colors"
        >
          {createMutation.isPending ? 'Creating...' : 'Create'}
        </button>
      </form>

      {isLoading && <p className="text-sm text-gray-500">Loading API keys...</p>}

      {keys && keys.length === 0 && (
        <p className="text-sm text-gray-400 text-center py-4">No API keys yet.</p>
      )}

      {keys && keys.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-xl divide-y divide-gray-100">
          {keys.map((key: ApiKey) => (
            <div key={key.id} className="flex items-center justify-between px-4 py-3">
              <div>
                <p className="text-sm font-medium text-gray-900">{key.name}</p>
                <p className="text-xs text-gray-500 font-mono">{key.keyPrefix}••••••••</p>
              </div>
              <button
                onClick={() => deleteMutation.mutate(key.id)}
                disabled={deleteMutation.isPending}
                className="text-xs text-red-600 hover:text-red-700 disabled:opacity-40"
              >
                Revoke
              </button>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
