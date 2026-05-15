
import { useState, useRef, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import { queryApi } from '../lib/api'
import SourceCard from '../components/SourceCard'
import type { QueryResponse } from '../types'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  response?: QueryResponse
  loading?: boolean
  error?: string
}

export default function AskPage() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const question = input.trim()
    if (!question || submitting) return

    const userMsg: Message = { id: crypto.randomUUID(), role: 'user', content: question }
    const assistantMsgId = crypto.randomUUID()
    const assistantMsg: Message = { id: assistantMsgId, role: 'assistant', content: '', loading: true }

    setMessages((prev) => [...prev, userMsg, assistantMsg])
    setInput('')
    setSubmitting(true)

    try {
      const res = await queryApi.ask(question)
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantMsgId
            ? { ...m, content: res.data.answer, response: res.data, loading: false }
            : m
        )
      )
    } catch {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantMsgId
            ? { ...m, content: '', error: 'Failed to get an answer. Please try again.', loading: false }
            : m
        )
      )
    } finally {
      setSubmitting(false)
      inputRef.current?.focus()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit(e as unknown as React.FormEvent)
    }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="px-8 py-5 border-b border-gray-200 bg-white">
        <h2 className="text-lg font-semibold text-gray-900">Ask</h2>
        <p className="text-sm text-gray-500">Query your organization's knowledge base</p>
      </div>

      <div className="flex-1 overflow-y-auto px-8 py-6 space-y-6">
        {messages.length === 0 && (
          <div className="text-center py-16">
            <p className="text-gray-400 text-sm">
              Ask anything about your organization's knowledge base.
              <br />
              Answers are grounded in your connected data sources.
            </p>
          </div>
        )}

        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            {msg.role === 'user' ? (
              <div className="max-w-xl bg-gray-900 text-white px-4 py-3 rounded-2xl rounded-tr-sm text-sm">
                {msg.content}
              </div>
            ) : (
              <div className="max-w-2xl w-full">
                {msg.loading ? (
                  <div className="flex gap-1 px-4 py-3">
                    <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce [animation-delay:-0.3s]" />
                    <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce [animation-delay:-0.15s]" />
                    <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" />
                  </div>
                ) : msg.error ? (
                  <p className="text-sm text-red-600 bg-red-50 px-4 py-3 rounded-xl">{msg.error}</p>
                ) : (
                  <div className="bg-white border border-gray-200 rounded-xl px-5 py-4">
                    <div className="prose prose-sm max-w-none text-gray-800">
                      <ReactMarkdown>{msg.content}</ReactMarkdown>
                    </div>

                    {msg.response && (
                      <>
                        {msg.response.concepts.length > 0 && (
                          <div className="mt-4 flex flex-wrap gap-1">
                            {msg.response.concepts.map((c) => (
                              <span key={c} className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full">
                                {c}
                              </span>
                            ))}
                          </div>
                        )}

                        {msg.response.sources.length > 0 && (
                          <div className="mt-4 space-y-2">
                            <p className="text-xs font-medium text-gray-500">Sources</p>
                            {msg.response.sources.map((s, i) => (
                              <SourceCard key={s.sourceId} source={s} index={i} />
                            ))}
                          </div>
                        )}

                        <p className="text-xs text-gray-400 mt-3">
                          {msg.response.cacheHit ? 'Cached · ' : ''}
                          {msg.response.durationMs}ms
                        </p>
                      </>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <div className="px-8 py-4 border-t border-gray-200 bg-white">
        <form onSubmit={handleSubmit} className="flex gap-3 items-end">
          <textarea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
            placeholder="Ask a question... (Enter to send, Shift+Enter for newline)"
            className="flex-1 resize-none px-4 py-2.5 text-sm border border-gray-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent min-h-[42px] max-h-32"
            style={{ height: 'auto' }}
            disabled={submitting}
          />
          <button
            type="submit"
            disabled={!input.trim() || submitting}
            className="px-4 py-2.5 bg-gray-900 text-white text-sm font-medium rounded-xl hover:bg-gray-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors shrink-0"
          >
            Send
          </button>
        </form>
      </div>
    </div>
  )
}
