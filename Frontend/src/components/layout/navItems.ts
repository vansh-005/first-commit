import { Home, Library, MessageSquare, Search } from 'lucide-react'

export const NAV_ITEMS = [
  { to: '/app', label: 'Home', icon: Home, end: true },
  { to: '/app/library', label: 'Library', icon: Library, end: false },
  { to: '/app/search', label: 'Search', icon: Search, end: false },
  { to: '/app/ask', label: 'Ask', icon: MessageSquare, end: false },
] as const
