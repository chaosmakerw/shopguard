<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import {
  Search,
  ArrowUpRight,
  ShoppingBag,
  Package,
  BookOpen,
  RotateCcw,
  Check,
  Clock3,
  X,
  Sparkles,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
  CreditCard,
  Minus,
  Plus,
} from "@lucide/vue";
import { api } from "../../../trade-web/src/api";
import { request } from "../api";
import { categories, type CatalogProduct, type PageResult } from "../commerce";
import ProductVisual from "./ProductVisual.vue";
import type { TradeOrder, TradeContext } from "../../../trade-web/src/types";
const props = defineProps<{ focusOrder?: string }>();
const emit = defineEmits<{
  ask: [order: TradeOrder];
  browse: [];
  aftersales: [id: string];
}>();
const category = ref("");
const tab = ref<"products" | "orders">("products"),
  keyword = ref(""),
  products = ref<CatalogProduct[]>([]),
  orders = ref<TradeOrder[]>([]);
const context = ref<TradeContext>(),
  selected = ref<TradeOrder | null>(null),
  product = ref<CatalogProduct | null>(null),
  quantity = ref(1);
const busy = ref(false),
  loading = ref(false),
  error = ref(""),
  notice = ref(""),
  page = ref(1),
  now = ref(Date.now()),
  pending = ref<"pay" | "cancel" | null>(null);
let createKey = "",
  paymentKey = "";
const timer = window.setInterval(() => (now.value = Date.now()), 1000);
const labels: Record<string, string> = {
  PENDING_PAYMENT: "待付款",
  PAID: "已支付",
  CANCELLED: "已关闭",
  REFUNDED: "已退款",
};
const pendingCount = computed(
  () => orders.value.filter((o) => o.status === "PENDING_PAYMENT").length,
);
const remaining = computed(() =>
  selected.value
    ? Math.max(
        0,
        Math.ceil((Date.parse(selected.value.expiresAt) - now.value) / 1000),
      )
    : 0,
);
const money = (value: string) => Number(value).toFixed(2);
const date = (value: string) =>
  new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
const fail = (e: unknown) =>
  (error.value = e instanceof Error ? e.message : "操作未完成，请重试");
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [p, o, c] = await Promise.all([
      request<PageResult<CatalogProduct>>(
        "/commerce/catalog?" +
          new URLSearchParams({
            keyword: keyword.value,
            category: category.value,
            page: String(tab.value === "products" ? page.value : 1),
            size: "12",
          }),
      ),
      api.orders(tab.value === "orders" ? page.value : 1, 12),
      api.context(),
    ]);
    products.value = p.items;
    orders.value = o.items;
    context.value = c;
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function switchTab(value: "products" | "orders") {
  tab.value = value;
  page.value = 1;
  await load();
}
async function openOrder(id: string) {
  error.value = "";
  try {
    selected.value = await api.order(id);
    tab.value = "orders";
    product.value = null;
    pending.value = null;
  } catch (e) {
    fail(e);
  }
}
function openProduct(value: CatalogProduct) {
  product.value = value;
  selected.value = null;
  quantity.value = 1;
  createKey = crypto.randomUUID();
  error.value = "";
}
async function create() {
  if (!product.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const order = await api.createOrder(
      { productId: product.value.productId, quantity: quantity.value },
      createKey,
    );
    product.value = null;
    selected.value = order;
    tab.value = "orders";
    page.value = 1;
    notice.value = "订单已创建，库存已为你预留。";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function commit() {
  if (!selected.value || !pending.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    selected.value =
      pending.value === "pay"
        ? await api.simulatePayment(selected.value.orderId, paymentKey)
        : await api.cancelOrder(selected.value.orderId);
    notice.value =
      pending.value === "pay"
        ? "支付演示完成，订单状态已更新。"
        : "订单已关闭，预占库存已释放。";
    pending.value = null;
    await load();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
function confirm(action: "pay" | "cancel") {
  pending.value = action;
  paymentKey = crypto.randomUUID();
}
watch(
  () => props.focusOrder,
  (id) => {
    if (id) void openOrder(id);
  },
);
onMounted(async () => {
  await load();
  if (props.focusOrder) await openOrder(props.focusOrder);
});
onUnmounted(() => clearInterval(timer));
</script>
<template>
  <div class="trade-desk content-page">
    <header class="page-heading">
      <div>
        <div class="overline">SHOPGUARD / COMMERCE</div>
        <h1>商品与订单</h1>
        <p>从发现商品，到每一笔订单的进展。</p>
      </div>
      <button class="secondary" :disabled="loading" @click="load">
        <RefreshCw :size="15" :class="{ spin: loading }" />刷新
      </button>
    </header>
    <div v-if="error" role="alert" class="alert error">
      {{ error
      }}<button @click="error = ''" aria-label="关闭错误">
        <X :size="16" />
      </button>
    </div>
    <div v-if="notice" role="status" class="alert success">
      {{ notice
      }}<button @click="notice = ''" aria-label="关闭提示">
        <X :size="16" />
      </button>
    </div>
    <div class="commerce-toolbar">
      <div class="segmented">
        <button
          :class="{ active: tab === 'products' }"
          @click="switchTab('products')"
        >
          <ShoppingBag :size="16" />商品</button
        ><button
          :class="{ active: tab === 'orders' }"
          @click="switchTab('orders')"
        >
          <Package :size="16" />我的订单<span
            v-if="pendingCount"
            class="count"
            >{{ pendingCount }}</span
          >
        </button>
      </div>
      <form
        v-if="tab === 'products'"
        class="search-field"
        @submit.prevent="
          page = 1;
          load();
        "
      >
        <Search :size="16" /><input
          v-model="keyword"
          placeholder="搜索商品名称"
          aria-label="搜索商品"
        /><button type="submit">搜索</button>
      </form>
      <span v-else class="muted">只显示当前账户的订单</span>
    </div>
    <div v-if="tab === 'products'" class="catalog-categories">
      <button
        :class="{ active: !category }"
        @click="
          category = '';
          page = 1;
          load();
        "
      >
        全部好物</button
      ><button
        v-for="item in categories"
        :key="item"
        :class="{ active: category === item }"
        @click="
          category = item;
          page = 1;
          load();
        "
      >
        {{ item }}
      </button>
    </div>
    <div
      class="commerce-layout"
      :class="{ 'with-detail': selected || product }"
    >
      <div class="commerce-main">
        <div v-if="loading && !products.length" class="loading-state">
          <RefreshCw class="spin" />正在读取商品…
        </div>
        <div v-else-if="tab === 'products'" class="product-grid">
          <button
            v-for="item in products"
            :key="item.productId"
            class="product-card"
            @click="openProduct(item)"
          >
            <ProductVisual :title="item.title" :category="item.category" />
            <div class="product-copy">
              <span class="stock" :class="{ empty: !item.availableQuantity }">{{
                item.availableQuantity > 0
                  ? "库存 " + item.availableQuantity
                  : "暂时售罄"
              }}</span>
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
              <div class="product-bottom">
                <strong><small>¥</small>{{ money(item.price) }}</strong
                ><span>查看商品 <ArrowUpRight :size="16" /></span>
              </div>
            </div>
          </button>
          <div v-if="!products.length" class="empty-state">
            <Search :size="32" />
            <h3>没有找到商品</h3>
            <p>试试其他关键词。</p>
          </div>
        </div>
        <div v-else class="order-list">
          <div v-if="!orders.length" class="empty-state">
            <ShoppingBag :size="36" />
            <h3>还没有订单</h3>
            <p>选择一件商品，开始你的第一笔交易。</p>
            <button class="primary" @click="switchTab('products')">
              去看看商品 <ArrowUpRight :size="15" />
            </button>
          </div>
          <button
            v-for="order in orders"
            :key="order.orderId"
            class="order-row"
            :class="{ selected: selected?.orderId === order.orderId }"
            @click="openOrder(order.orderId)"
          >
            <span class="order-thumb"><BookOpen :size="24" /></span
            ><span class="order-info"
              ><span class="mono">{{ order.orderId }}</span
              ><strong>{{ order.productName }}</strong
              ><span
                >{{ order.quantity }} 件 · {{ date(order.createdAt) }}</span
              ></span
            ><span class="order-amount"
              ><strong>¥ {{ money(order.totalAmount) }}</strong
              ><span class="pill" :class="order.status.toLowerCase()">{{
                labels[order.status]
              }}</span></span
            ><ArrowUpRight :size="16" />
          </button>
        </div>
        <div class="pagination">
          <span>第 {{ page }} 页</span
          ><button
            class="icon-button"
            aria-label="上一页"
            :disabled="page === 1 || loading"
            @click="
              page--;
              load();
            "
          >
            <ChevronLeft :size="17" /></button
          ><button
            class="icon-button"
            aria-label="下一页"
            :disabled="
              (tab === 'products' ? products : orders).length < 12 || loading
            "
            @click="
              page++;
              load();
            "
          >
            <ChevronRight :size="17" />
          </button>
        </div>
      </div>
      <aside v-if="product" class="detail-panel">
        <div class="detail-top">
          <span>商品详情</span
          ><button
            class="icon-button"
            aria-label="关闭商品详情"
            :disabled="busy"
            @click="product = null"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="detail-illustration">
          <ProductVisual :title="product.title" :category="product.category" />
        </div>
        <h2>{{ product.title }}</h2>
        <p class="detail-description">{{ product.description }}</p>
        <dl>
          <div>
            <dt>单价</dt>
            <dd>¥ {{ money(product.price) }}</dd>
          </div>
          <div>
            <dt>可售库存</dt>
            <dd>{{ product.availableQuantity }} 件</dd>
          </div>
        </dl>
        <div class="quantity-row">
          <span>购买数量</span>
          <div class="quantity-control">
            <button
              :disabled="quantity <= 1 || busy"
              aria-label="减少数量"
              @click="quantity--"
            >
              <Minus :size="14" /></button
            ><input
              v-model.number="quantity"
              type="number"
              min="1"
              :max="Math.min(20, product.availableQuantity)"
              :disabled="busy"
              aria-label="购买数量"
            /><button
              :disabled="
                quantity >= product.availableQuantity || quantity >= 20 || busy
              "
              aria-label="增加数量"
              @click="quantity++"
            >
              <Plus :size="14" />
            </button>
          </div>
        </div>
        <div class="checkout-total">
          <span>预计合计</span
          ><strong
            >¥ {{ (Number(product.price) * quantity).toFixed(2) }}</strong
          >
        </div>
        <button
          class="primary wide"
          :disabled="
            busy ||
            !Number.isInteger(quantity) ||
            quantity < 1 ||
            quantity > Math.min(20, product.availableQuantity)
          "
          @click="create"
        >
          <ShoppingBag :size="17" />{{ busy ? "正在提交…" : "提交订单" }}
        </button>
        <p class="detail-note">
          <ShieldCheck :size="14" />金额与库存由服务器核对
        </p>
      </aside>
      <aside
        v-else-if="selected"
        class="detail-panel"
        data-testid="order-detail"
      >
        <div class="detail-top">
          <span>订单详情</span
          ><button
            class="icon-button"
            aria-label="关闭订单详情"
            :disabled="busy"
            @click="selected = null"
          >
            <X :size="18" />
          </button>
        </div>
        <span class="pill" :class="selected.status.toLowerCase()">{{
          labels[selected.status]
        }}</span>
        <h2>{{ selected.productName }}</h2>
        <div class="mono order-id">{{ selected.orderId }}</div>
        <dl>
          <div>
            <dt>购买数量</dt>
            <dd>{{ selected.quantity }} 件</dd>
          </div>
          <div>
            <dt>订单金额</dt>
            <dd class="amount">¥ {{ money(selected.totalAmount) }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ date(selected.createdAt) }}</dd>
          </div>
        </dl>
        <div class="order-timeline">
          <div class="timeline-item done">
            <span><Check :size="13" /></span>
            <div>
              <strong>订单已创建</strong
              ><small>{{ date(selected.createdAt) }}</small>
            </div>
          </div>
          <div
            class="timeline-item"
            :class="{
              done: selected.status !== 'CANCELLED',
              closed: selected.status === 'CANCELLED',
            }"
          >
            <span
              ><component
                :is="selected.status === 'CANCELLED' ? X : Check"
                :size="13"
            /></span>
            <div>
              <strong>{{
                selected.status === "CANCELLED"
                  ? "订单关闭，库存已释放"
                  : "库存预占已完成"
              }}</strong
              ><small>{{
                selected.status === "CANCELLED"
                  ? "可重新选购商品"
                  : "创建订单时与库存变更一起提交"
              }}</small>
            </div>
          </div>
          <div
            v-if="selected.status !== 'CANCELLED'"
            class="timeline-item"
            :class="{ done: ['PAID', 'REFUNDED'].includes(selected.status) }"
          >
            <span
              ><Check
                v-if="['PAID', 'REFUNDED'].includes(selected.status)"
                :size="13" /><Clock3 v-else :size="13"
            /></span>
            <div>
              <strong>{{
                selected.status === "REFUNDED"
                  ? "售后退款已完成"
                  : selected.status === "PAID"
                    ? "支付已完成"
                    : "等待付款"
              }}</strong
              ><small>{{
                selected.status === "REFUNDED"
                  ? "模拟退款已记录，库存已归还"
                  : selected.status === "PAID"
                    ? "当前数据库状态：已支付"
                    : remaining > 0
                      ? `剩余 ${Math.floor(remaining / 60)} 分 ${remaining % 60} 秒`
                      : "支付期限已到，请刷新订单"
              }}</small>
            </div>
          </div>
        </div>
        <div v-if="pending" class="confirmation">
          <strong>{{
            pending === "pay" ? "确认模拟支付？" : "确认取消订单？"
          }}</strong>
          <p>
            {{
              pending === "pay"
                ? "仅更新演示支付记录，不扣除真实资金。"
                : "关闭订单后释放预占库存。"
            }}
          </p>
          <div>
            <button class="secondary" :disabled="busy" @click="pending = null">
              返回</button
            ><button class="primary" :disabled="busy" @click="commit">
              {{ busy ? "处理中…" : "确认操作" }}
            </button>
          </div>
        </div>
        <div v-else class="order-actions">
          <button
            class="ai-action"
            :disabled="busy"
            @click="emit('ask', selected)"
          >
            <Sparkles :size="17" /><span
              >让 AI 查询这笔订单<small>通过业务工具核对实时状态</small></span
            ><ArrowUpRight :size="17" /></button
          ><button
            v-if="
              selected.status === 'PENDING_PAYMENT' &&
              context?.simulatePaymentEnabled
            "
            class="primary wide"
            :disabled="busy || remaining === 0"
            @click="confirm('pay')"
          >
            <CreditCard :size="16" />模拟付款</button
          ><button
            v-if="selected.status === 'PENDING_PAYMENT'"
            class="secondary wide"
            :disabled="busy"
            @click="confirm('cancel')"
          >
            取消订单
          </button>
          <button
            v-if="['PAID', 'REFUNDED'].includes(selected.status)"
            class="secondary wide"
            @click="emit('aftersales', selected.orderId)"
          >
            <RotateCcw :size="16" />{{
              selected.status === "REFUNDED"
                ? "查看售后记录"
                : "申请 / 查看售后"
            }}
          </button>
        </div>
        <p v-if="context?.simulatePaymentEnabled" class="detail-note">
          支付演示 · 不涉及真实资金
        </p>
      </aside>
    </div>
  </div>
</template>
