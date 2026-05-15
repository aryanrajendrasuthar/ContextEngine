
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { authApi } from '../lib/api'
import clsx from 'clsx'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/ask', label: 'Ask' },
  { to: '/explorer', label: 'Explorer' },
  { to: '/sources', label: 'Sources' },
  { to: '/people', label: 'People' },
  { to: '/settings', label: 'Settings' },
]

export default function Layout({ children }: { children: React.ReactNode }) {
  const { email, displayName, organizationName, clearAuth } = useAuthStore()
  const navigate = useNavigate()

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } finally {
      clearAuth()
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="flex h-screen bg-gray-50">
      <aside className="w-60 shrink-0 bg-white border-r border-gray-200 flex flex-col">
        <div className="px-6 py-5 border-b border-gray-200">
          <h1 className="text-base font-semibold text-gray-900 tracking-tight">ContextEngine</h1>
          {organizationName && (
            <p className="text-xs text-gray-500 mt-0.5 truncate">{organizationName}</p>
          )}
        </div>

        <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
          {NAV_ITEMS.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                clsx(
                  'block px-3 py-2 text-sm rounded-md transition-colors',
                  isActive
                    ? 'bg-gray-100 text-gray-900 font-medium'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                )
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="px-4 py-4 border-t border-gray-200">
          <p className="text-xs font-medium text-gray-900 truncate">{displayName ?? email}</p>
          <p className="text-xs text-gray-500 truncate">{email}</p>
          <button
            onClick={handleLogout}
            className="mt-2 text-xs text-red-600 hover:text-red-700"
          >
            Sign out
          </button>
        </div>
      </aside>

      <main className="flex-1 min-w-0 overflow-y-auto">
        {children}
      </main>
    </div>
  )
}
