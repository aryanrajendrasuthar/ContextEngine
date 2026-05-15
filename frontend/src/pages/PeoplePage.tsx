
import { useQuery } from '@tanstack/react-query'
import { useRef, useEffect } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import { graphApi } from '../lib/api'
import type { GraphNode, GraphEdge } from '../types'

const NODE_COLORS: Record<string, string> = {
  Person: '#3b82f6',
  Concept: '#8b5cf6',
  Document: '#10b981',
  Decision: '#f59e0b',
}

export default function PeoplePage() {
  const containerRef = useRef<HTMLDivElement>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ['graph-people'],
    queryFn: () => graphApi.getPeople().then((r) => r.data),
    retry: false,
  })

  const graphData = data
    ? {
        nodes: data.nodes.map((n: GraphNode) => ({
          id: n.id,
          name: n.label,
          type: n.type,
          val: n.type === 'Person' ? 3 : 1,
        })),
        links: data.edges.map((e: GraphEdge) => ({
          source: e.source,
          target: e.target,
          label: e.type,
        })),
      }
    : { nodes: [], links: [] }

  return (
    <div className="flex flex-col h-full">
      <div className="px-8 py-5 border-b border-gray-200 bg-white">
        <h2 className="text-lg font-semibold text-gray-900">People Graph</h2>
        <p className="text-sm text-gray-500">
          Who knows what — based on authored documents and mentioned concepts
        </p>
      </div>

      <div className="flex-1 relative" ref={containerRef}>
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">
            Loading knowledge graph...
          </div>
        )}

        {error && (
          <div className="absolute inset-0 flex items-center justify-center">
            <p className="text-sm text-red-600 bg-red-50 px-4 py-3 rounded-lg">
              Failed to load graph data. Make sure Neo4j is running and has been populated.
            </p>
          </div>
        )}

        {data && data.nodes.length === 0 && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
            No people or concepts in the knowledge graph yet. Ingest some content first.
          </div>
        )}

        {data && data.nodes.length > 0 && containerRef.current && (
          <ForceGraph2D
            graphData={graphData}
            width={containerRef.current.clientWidth}
            height={containerRef.current.clientHeight}
            nodeLabel="name"
            nodeColor={(node: { type?: string }) => NODE_COLORS[node.type ?? ''] ?? '#6b7280'}
            nodeRelSize={6}
            linkDirectionalArrowLength={4}
            linkDirectionalArrowRelPos={1}
            linkColor={() => '#e5e7eb'}
            backgroundColor="#f9fafb"
          />
        )}
      </div>

      {data && data.nodes.length > 0 && (
        <div className="px-8 py-3 border-t border-gray-200 bg-white flex gap-4">
          {Object.entries(NODE_COLORS).map(([type, color]) => (
            <div key={type} className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: color }} />
              <span className="text-xs text-gray-600">{type}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
