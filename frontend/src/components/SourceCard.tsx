
import type { SourceDocument } from '../types'

interface Props {
  source: SourceDocument
  index: number
}

export default function SourceCard({ source, index }: Props) {
  return (
    <div className="flex gap-3 p-3 bg-gray-50 rounded-lg border border-gray-200">
      <span className="shrink-0 w-5 h-5 rounded-full bg-gray-200 text-gray-600 text-xs flex items-center justify-center font-medium">
        {index + 1}
      </span>
      <div className="min-w-0">
        <p className="text-xs font-medium text-gray-700 uppercase tracking-wide">
          {source.sourceType}
        </p>
        {source.title && (
          <p className="text-sm text-gray-900 mt-0.5 truncate">{source.title}</p>
        )}
        {source.url && (
          <a
            href={source.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-blue-600 hover:underline mt-0.5 block truncate"
          >
            {source.url}
          </a>
        )}
        <p className="text-xs text-gray-400 mt-1">
          Relevance: {(source.score * 100).toFixed(0)}%
        </p>
      </div>
    </div>
  )
}
