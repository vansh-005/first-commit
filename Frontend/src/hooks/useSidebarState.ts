import { useCallback, useEffect, useState } from 'react'

const STORAGE_KEY = 'memory-layer:sidebar'
const DEFAULT_WIDTH = 240
const MIN_WIDTH = 200
const MAX_WIDTH = 320
export const COLLAPSED_WIDTH = 64

interface StoredSidebarState {
  collapsed: boolean
  width: number
}

function readStoredState(): StoredSidebarState {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return { collapsed: false, width: DEFAULT_WIDTH }
    const parsed = JSON.parse(raw) as Partial<StoredSidebarState>
    return {
      collapsed: parsed.collapsed ?? false,
      width: clampWidth(parsed.width ?? DEFAULT_WIDTH),
    }
  } catch {
    // Private browsing / blocked storage — fall back to defaults rather than throwing.
    return { collapsed: false, width: DEFAULT_WIDTH }
  }
}

function clampWidth(width: number) {
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, width))
}

/** Docs/FRONTEND.md §6 — remembers the sidebar's collapsed/expanded state and resized width
 * locally, per viewer, across reloads. */
export function useSidebarState() {
  const [state, setState] = useState<StoredSidebarState>(() => readStoredState())

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // Ignore — losing the remembered width/collapsed state is not worth surfacing an error.
    }
  }, [state])

  const toggleCollapsed = useCallback(() => {
    setState((prev) => ({ ...prev, collapsed: !prev.collapsed }))
  }, [])

  const setWidth = useCallback((width: number) => {
    setState((prev) => ({ ...prev, width: clampWidth(width) }))
  }, [])

  return {
    collapsed: state.collapsed,
    width: state.collapsed ? COLLAPSED_WIDTH : state.width,
    toggleCollapsed,
    setWidth,
    minWidth: MIN_WIDTH,
    maxWidth: MAX_WIDTH,
  }
}
