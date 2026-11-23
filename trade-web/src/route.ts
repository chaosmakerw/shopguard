export interface Route {
  view: 'products' | 'orders'
  id: string | null
  page: number
  keyword: string
}

export function readRoute(): Route {
  const [path = '', query = ''] = window.location.hash.slice(1).split('?')
  const parts = path.split('/').filter(Boolean)
  const params = new URLSearchParams(query)
  const page = Number(params.get('page') ?? 1)
  let id: string | null = null
  try {
    id = parts[1] ? decodeURIComponent(parts[1]) : null
  } catch {
    // An invalid deep link falls back to its list; no malformed ID is sent.
  }
  return {
    view: parts[0] === 'orders' ? 'orders' : 'products',
    id,
    page: Number.isSafeInteger(page) && page > 0 ? page : 1,
    keyword: (params.get('keyword') ?? '').slice(0, 100),
  }
}

export function routeHref(route: Route, changes: Partial<Route> = {}): string {
  const target = { ...route, ...changes }
  const params = new URLSearchParams()
  if (target.view === 'products' && target.keyword) params.set('keyword', target.keyword)
  if (target.page > 1) params.set('page', String(target.page))
  const query = params.size ? `?${params}` : ''
  return `#/${target.view}${target.id ? `/${encodeURIComponent(target.id)}` : ''}${query}`
}
