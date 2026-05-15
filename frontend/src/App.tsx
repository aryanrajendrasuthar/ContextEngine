
import { Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import AskPage from './pages/AskPage'
import ExplorerPage from './pages/ExplorerPage'
import SourcesPage from './pages/SourcesPage'
import PeoplePage from './pages/PeoplePage'
import SettingsPage from './pages/SettingsPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 300_000,
    },
  },
})

function AuthenticatedApp() {
  return (
    <Layout>
      <Routes>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/ask" element={<AskPage />} />
        <Route path="/explorer" element={<ExplorerPage />} />
        <Route path="/sources" element={<SourcesPage />} />
        <Route path="/people" element={<PeoplePage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Layout>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/*"
          element={
            <ProtectedRoute>
              <AuthenticatedApp />
            </ProtectedRoute>
          }
        />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </QueryClientProvider>
  )
}
