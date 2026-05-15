
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { connectorApi } from '../lib/api'
import type { Connector } from '../types'
import clsx from 'clsx'

const STATUS_STYLES: Record<Connector['status'], string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  INACTIVE: 'bg-gray-100 text-gray-600',
  ERROR: 'bg-red-100 text-red-700',
}

export default function SourcesPage() {
  const queryClient = useQueryClient()

  const { data: connectors, isLoading, error } = useQuery({
    queryKey: ['connectors'],
    queryFn: () => connectorApi.list().then((r) => r.data),
    retry: false,
  })

  const activate = useMutation({
    mutationFn: (id: string) => connectorApi.activate(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['connectors'] }),
  })

  const deactivate = useMutation({
    mutationFn: (id: string) => connectorApi.deactivate(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['connectors'] }),
  })

  return (
    <div className="p-8">
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-gray-900">Sources</h2>
        <p className="text-sm text-gray-500 mt-1">
          Manage connectors that feed content into your knowledge base
        </p>
      </div>

      {isLoading && <p className="text-sm text-gray-500">Loading connectors...</p>}

      {error && (
        <div className="text-sm text-red-600 bg-red-50 px-4 py-3 rounded-lg">
          Failed to load connectors. The connector service may be unavailable.
        </div>
      )}

      {connectors && connectors.length === 0 && (
        <div className="text-center py-16 text-gray-400 text-sm">
          No connectors configured yet. Connectors are set up via the connector-service API.
        </div>
      )}

      {connectors && connectors.length > 0 && (
        <div className="space-y-3 max-w-3xl">
          {connectors.map((connector) => (
            <ConnectorRow
              key={connector.id}
              connector={connector}
              onActivate={() => activate.mutate(connector.id)}
              onDeactivate={() => deactivate.mutate(connector.id)}
              busy={activate.isPending || deactivate.isPending}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function ConnectorRow({
  connector,
  onActivate,
  onDeactivate,
  busy,
}: {
  connector: Connector
  onActivate: () => void
  onDeactivate: () => void
  busy: boolean
}) {
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4 flex items-center justify-between gap-4">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-gray-900 truncate">{connector.name}</p>
          <span className={clsx('text-xs font-medium px-2 py-0.5 rounded-full', STATUS_STYLES[connector.status])}>
            {connector.status}
          </span>
        </div>
        <p className="text-xs text-gray-500 mt-0.5 uppercase tracking-wide">{connector.sourceType}</p>
      </div>

      <div className="shrink-0">
        {connector.status === 'ACTIVE' ? (
          <button
            onClick={onDeactivate}
            disabled={busy}
            className="text-xs text-gray-600 border border-gray-300 px-3 py-1.5 rounded-lg hover:bg-gray-50 disabled:opacity-40 transition-colors"
          >
            Pause
          </button>
        ) : (
          <button
            onClick={onActivate}
            disabled={busy}
            className="text-xs text-gray-900 border border-gray-900 px-3 py-1.5 rounded-lg hover:bg-gray-50 disabled:opacity-40 transition-colors"
          >
            Activate
          </button>
        )}
      </div>
    </div>
  )
}
