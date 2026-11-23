export interface TradeContext {
  mode: 'LOCAL_DEMO' | 'JWT' | 'SESSION'
  userId: string
  simulatePaymentEnabled: boolean
  paymentWindowSeconds: number
}

export interface Product {
  productId: string
  title: string
  description: string
  price: string
  availableQuantity: number
}

export interface TradeOrder {
  orderId: string
  productId: string
  productName: string
  quantity: number
  totalAmount: string
  status: 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'REFUNDED'
  createdAt: string
  expiresAt: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
}

export interface OrderRequest {
  productId: string
  quantity: number
}
