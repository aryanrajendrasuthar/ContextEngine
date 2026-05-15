
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { graphApi } from '../lib/api'
import type { GraphNode } from '../types'

const TYPE_COLORS: Record<string, string> = {
  Person: 'bg-blue-100 text-blue-700',
  Document: 'bg-green-100 text-green-700',
  Concept: 'bg-purple-100 text-purple-700',
  Decision: 'bg-orange-100 text-orange-700',
}

export default function ExplorerPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [activeQuery, setActiveQuery] = useState('')

  const { data, isLoading, error } = useQuery({
    queryKey: ['graph-explore', activeQuery],
    queryFn: () => graphApi.explore(activeQuery).then((r) => r.data),
    enabled: activeQuery.length > 0,
    retry: false,
  })

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = searchQuery.trim()
    if (q) setActiveQuery(q)
  }

  return (
    <div className="p-8">
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-gray-900">Knowledge Explorer</h2>
        <p className="text-sm text-gray-500 mt-1">
          Search concepts, technologies, and decisions in your knowledge graph
        </p>
      </div>

      <form onSubmit={handleSearch} className="flex gap-3 mb-8 max-w-xl">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search for a concept, technology, or topic..."
          className="flex-1 px-4 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent"
        />
        <button
          type="submit"
          disabled={!searchQuery.trim()}
          className="px-4 py-2 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-800 disabled:opacity-40 transition-colors"
        >
          Search
        </button>
      </form>

      {!activeQuery && (
        <div className="text-center py-16 text-gray-400 text-sm">
          Enter a keyword to explore connected concepts, documents, and people.
        </div>
      )}

      {isLoading && (
        <div className="text-sm text-gray-500">Searching knowledge graph...</div>
      )}

      {error && (
        <div className="text-sm text-red-600 bg-red-50 px-4 py-3 rounded-lg">
          Failed to query the knowledge graph. Make sure Neo4j is running.
        </div>
      )}

      {data && (
        <div>
          <p className="text-xs text-gray-500 mb-4">
            {data.nodes.length} nodes · {data.edges.length} relationships
          </p>

          <div className="grid grid-cols-1 gap-3">
            {data.nodes.map((node) => (
              <NodeCard key={node.id} node={node} edges={data.edges.filter(e => e.source === node.id || e.target === node.id)} />
            ))}
          </div>

          {data.nodes.length === 0 && (
            <p className="text-sm text-gray-500 text-center py-8">
              No results found for "{activeQuery}".
            </p>
          )}
        </div>
      )}
    </div>
  )
}

function NodeCard({ node, edges }: { node: GraphNode; edges: { source: string; target: string; type: string }[] }) {
  const colorClass = TYPE_COLORS[node.type] ?? 'bg-gray-100 text-gray-700'

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4">
      <div className="flex items-start gap-3">
        <span className={`text-xs font-medium px-2 py-0.5 rounded-full shrink-0 ${colorClass}`}>
          {node.type}
        </span>
        <div className="min-w-0">
          <p className="text-sm font-medium text-gray-900 truncate">{node.label}</p>
          {Object.entries(node.properties).map(([key, val]) => (
            val != null && (
              <p key={key} className="text-xs text-gray-500 mt-0.5">
                {key}: {String(val)}
              </p>
            )
          ))}
          {edges.length > 0 && (
            <p className="text-xs text-gray-400 mt-1">{edges.length} connection{edges.length !== 1 ? 's' : ''}</p>
          )}
        </div>
      </div>
    </div>
  )
}
