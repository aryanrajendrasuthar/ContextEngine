
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { connectorApi } from '../lib/api'

export default function DashboardPage() {
  const { displayName, email, organizationName, role } = useAuthStore()
  const navigate = useNavigate()

  const { data: connectors, isLoading: loadingConnectors } = useQuery({
    queryKey: ['connectors'],
    queryFn: () => connectorApi.list().then((r) => r.data),
    retry: false,
  })

  const activeConnectors = connectors?.filter((c) => c.status === 'ACTIVE') ?? []
  const errorConnectors = connectors?.filter((c) => c.status === 'ERROR') ?? []

  return (
    <div className="p-8 max-w-5xl">
      <div className="mb-8">
        <h2 className="text-xl font-semibold text-gray-900">
          Welcome back, {displayName ?? email}
        </h2>
        <p className="text-sm text-gray-500 mt-1">{organizationName}</p>
      </div>

      <div className="grid grid-cols-3 gap-4 mb-8">
        <StatCard
          label="Active connectors"
          value={loadingConnectors ? '—' : String(activeConnectors.length)}
          sublabel={`of ${connectors?.length ?? 0} total`}
        />
        <StatCard
          label="Connectors with errors"
          value={loadingConnectors ? '—' : String(errorConnectors.length)}
          sublabel="need attention"
          highlight={errorConnectors.length > 0}
        />
        <StatCard
          label="Your role"
          value={role ?? '—'}
          sublabel="in this organization"
        />
      </div>

      <div className="grid grid-cols-2 gap-6">
        <QuickAction
          title="Ask a question"
          description="Query your organization's knowledge base in natural language."
          action="Go to Ask"
          onClick={() => navigate('/ask')}
        />
        <QuickAction
          title="Explore the graph"
          description="Visually browse concepts, people, and decisions in your knowledge graph."
          action="Open Explorer"
          onClick={() => navigate('/explorer')}
        />
        <QuickAction
          title="Manage sources"
          description="Connect new data sources or check the status of existing connectors."
          action="View Sources"
          onClick={() => navigate('/sources')}
        />
        <QuickAction
          title="People graph"
          description="See who knows what across your organization based on authored content."
          action="View People"
          onClick={() => navigate('/people')}
        />
      </div>
    </div>
  )
}

function StatCard({
  label,
  value,
  sublabel,
  highlight = false,
}: {
  label: string
  value: string
  sublabel: string
  highlight?: boolean
}) {
  return (
    <div className={`bg-white rounded-xl border p-5 ${highlight ? 'border-red-200' : 'border-gray-200'}`}>
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`text-3xl font-semibold mt-1 ${highlight ? 'text-red-600' : 'text-gray-900'}`}>
        {value}
      </p>
      <p className="text-xs text-gray-400 mt-1">{sublabel}</p>
    </div>
  )
}

function QuickAction({
  title,
  description,
  action,
  onClick,
}: {
  title: string
  description: string
  action: string
  onClick: () => void
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5">
      <h3 className="text-sm font-medium text-gray-900">{title}</h3>
      <p className="text-sm text-gray-500 mt-1">{description}</p>
      <button
        onClick={onClick}
        className="mt-3 text-sm text-gray-900 font-medium hover:underline"
      >
        {action} &rarr;
      </button>
    </div>
  )
}
