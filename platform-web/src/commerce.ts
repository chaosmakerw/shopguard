export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface CatalogProduct {
  productId: string;
  title: string;
  description: string;
  price: string;
  available: boolean;
  category: string;
  availableQuantity: number;
  reservedQuantity: number;
  soldQuantity: number;
  version: number;
}
export interface ManagedOrder {
  orderId: string;
  buyerId: string;
  buyerName: string;
  productName: string;
  totalAmount: string;
  quantity: number;
  status: string;
  createdAt: number;
  returnStatus?: string;
}
export interface ReturnCase {
  id: string;
  orderId: string;
  buyerId: string;
  productId: string;
  productName: string;
  quantity: number;
  type: string;
  reason: string;
  note: string;
  status: string;
  amount: string;
  carrier: string;
  trackingNumber: string;
  decisionNote: string;
  createdAt: number;
  updatedAt: number;
  version: number;
}
export interface ReturnEvent {
  fromStatus: string;
  toStatus: string;
  actorRole: string;
  note: string;
  createdAt: number;
}
export const labels: Record<string, string> = {
  PENDING_PAYMENT: "待付款",
  PAID: "已支付",
  CANCELLED: "已关闭",
  REFUNDED: "已退款",
  REQUESTED: "待审核",
  APPROVED: "待寄回",
  RETURNED: "待收货",
  REJECTED: "已驳回",
};
export const returnLabels: Record<string, string> = {
  ...labels,
  CANCELLED: "已撤销",
};
export const categories = ["书籍", "数码", "生活", "其他"];
export const money = (value: string | number) =>
  Number(value).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
export const date = (value: number | string) =>
  new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
export const errorText = (error: unknown) =>
  error instanceof Error ? error.message : "操作未完成，请重试";
