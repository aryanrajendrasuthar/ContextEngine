
import { Routes, Route, Navigate } from 'react-router-dom'

function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="text-center">
        <h1 className="text-2xl font-semibold text-gray-900">{title}</h1>
        <p className="mt-2 text-gray-500">This page is implemented in Sprint 5.</p>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<PlaceholderPage title="Login" />} />
      <Route path="/dashboard" element={<PlaceholderPage title="Dashboard" />} />
      <Route path="/ask" element={<PlaceholderPage title="Ask" />} />
      <Route path="/explorer" element={<PlaceholderPage title="Knowledge Explorer" />} />
      <Route path="/sources" element={<PlaceholderPage title="Sources" />} />
      <Route path="/people" element={<PlaceholderPage title="People Graph" />} />
      <Route path="/settings" element={<PlaceholderPage title="Settings" />} />
    </Routes>
  )
}
