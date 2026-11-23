import type { OrderRequest, Page, Product, TradeContext, TradeOrder } from './types'

// Authentication is deliberately memory-only. No token is written to browser storage.
let bearerToken = ''
let csrfToken = ''

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function setToken(value: string): void {
  bearerToken = value.trim().replace(/^Bearer\s+/i, '')
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 15_000)
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (bearerToken) headers.set('Authorization', `Bearer ${bearerToken}`)
  if (!bearerToken && init.method && init.method !== 'GET') {
    if (!csrfToken) {
      const session = await fetch('/api/platform/session', { credentials: 'same-origin', cache: 'no-store' })
      if (session.ok) csrfToken = (await session.json()).csrf ?? ''
    }
    if (csrfToken) headers.set('X-CSRF-TOKEN', csrfToken)
  }

  try {
    const response = await fetch(`/api/trade${path}`, {
      ...init,
      headers,
      signal: controller.signal,
      cache: 'no-store',
      credentials: 'same-origin',
    })
    const body: unknown = await response.json().catch(() => null)
    if (!response.ok) {
      if (response.status === 403) csrfToken = ''
      const error = body && typeof body === 'object' ? body as Record<string, unknown> : {}
      throw new ApiError(
        typeof error.message === 'string' ? error.message : '暂时无法完成操作，请稍后重试。',
        response.status,
        typeof error.code === 'string' ? error.code : `HTTP_${response.status}`,
      )
    }
    if (!body) throw new ApiError('未收到完整结果，请刷新确认。', 0, 'INCOMPLETE_RESPONSE')
    return body as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('连接中断或等待超时，请检查网络后重试。', 0, 'NETWORK_ERROR')
  } finally {
    window.clearTimeout(timeout)
  }
}

export const api = {
  context: () => request<TradeContext>('/context'),
  products: (keyword: string, page: number, size: number) => request<Page<Product>>(
    `/products?${new URLSearchParams({ keyword, page: String(page), size: String(size) })}`,
  ),
  product: (id: string) => request<Product>(`/products/${encodeURIComponent(id)}`),
  orders: (page: number, size: number) => request<Page<TradeOrder>>(
    `/orders?${new URLSearchParams({ page: String(page), size: String(size) })}`,
  ),
  order: (id: string) => request<TradeOrder>(`/orders/${encodeURIComponent(id)}`),
  createOrder: (body: OrderRequest, key: string) => request<TradeOrder>('/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify(body),
  }),
  cancelOrder: (id: string) => request<TradeOrder>(`/orders/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
    body: '{}',
  }),
  simulatePayment: (id: string, key: string) => request<TradeOrder>(
    `/orders/${encodeURIComponent(id)}/simulate-payment`, {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: '{}',
    },
  ),
}
