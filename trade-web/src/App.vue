<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, ApiError, setToken } from './api'
import { readRoute, routeHref } from './route'
import type { Product, TradeContext, TradeOrder } from './types'

const PAGE_SIZE = 12
const MAX_QUANTITY = 20
const route = ref(readRoute())
const context = ref<TradeContext | null>(null)
const connectionBusy = ref(true)
const connectionError = ref('')
const needsToken = ref(false)
const tokenDraft = ref('')
const authMessage = ref('请使用已有的访问凭证连接账号。')
const searchInput = ref(route.value.keyword)
const products = ref<Product[]>([])
const orders = ref<TradeOrder[]>([])
const product = ref<Product | null>(null)
const order = ref<TradeOrder | null>(null)
const listBusy = ref(false)
const detailBusy = ref(false)
const listError = ref('')
const detailError = ref('')
const actionBusy = ref(false)
const actionError = ref('')
const notice = ref('')
const quantity = ref<number | string>(1)
const cancelConfirmation = ref(false)
const now = ref(Date.now())
const detailHeading = ref<HTMLElement | null>(null)
const listHeading = ref<HTMLElement | null>(null)
const createAttempts = new Map<string, string>()
const paymentAttempts = new Map<string, string>()
const uncertainCreate = ref('')
let sessionVersion = 0
let loadVersion = 0
let ticker = 0

const quantityLimit = computed(() => Math.min(product.value?.availableQuantity ?? 0, MAX_QUANTITY))
const validQuantity = computed(() => typeof quantity.value === 'number'
  && Number.isInteger(quantity.value)
  && quantity.value >= 1
  && quantity.value <= quantityLimit.value)
const currentPayload = computed(() => product.value
  ? JSON.stringify({ productId: product.value.productId, quantity: quantity.value })
  : '')
const retryingCreate = computed(() => uncertainCreate.value !== ''
  && uncertainCreate.value === currentPayload.value)
const hasNextPage = computed(() => (route.value.view === 'products' ? products.value : orders.value).length === PAGE_SIZE)
const listHref = computed(() => routeHref(route.value, { id: null }))
const remainingTime = computed(() => {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT') return ''
  const seconds = Math.max(0, Math.ceil((Date.parse(order.value.expiresAt) - now.value) / 1000))
  if (!seconds) return '付款时间已到，请刷新确认订单状态。'
  return `还可付款 ${Math.floor(seconds / 60)} 分 ${String(seconds % 60).padStart(2, '0')} 秒`
})
const subtotal = computed(() => {
  if (!product.value || !validQuantity.value) return '—'
  // Prices are decimal strings; cents stay integers throughout the calculation.
  const [whole = '0', fractional = ''] = product.value.price.split('.')
  if (!/^\d+$/.test(whole) || !/^\d{0,2}$/.test(fractional)) return '—'
  const cents = (BigInt(whole) * 100n + BigInt(fractional.padEnd(2, '0'))) * BigInt(quantity.value)
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
})

function price(value: string): string {
  const [whole, decimal = ''] = value.split('.')
  return `${whole}.${decimal.padEnd(2, '0')}`
}

function dateTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '时间暂不可用' : new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date)
}

function statusLabel(status: TradeOrder['status']): string {
  return { PENDING_PAYMENT: '待付款', PAID: '已付款', CANCELLED: '已关闭', REFUNDED: '已退款' }[status]
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : '暂时无法完成操作，请稍后重试。'
}

function clearSession(): void {
  sessionVersion++
  loadVersion++
  setToken('')
  tokenDraft.value = ''
  context.value = null
  products.value = []
  orders.value = []
  product.value = null
  order.value = null
  createAttempts.clear()
  paymentAttempts.clear()
  uncertainCreate.value = ''
  actionError.value = ''
  notice.value = ''
  listBusy.value = false
  detailBusy.value = false
}

function handleUnauthorized(error: unknown): boolean {
  if (!(error instanceof ApiError) || error.status !== 401) return false
  clearSession()
  needsToken.value = true
  authMessage.value = '当前连接需要有效的访问凭证，请重新连接。'
  return true
}

async function connect(): Promise<void> {
  if (connectionBusy.value && context.value) return
  connectionBusy.value = true
  connectionError.value = ''
  try {
    context.value = await api.context()
    needsToken.value = false
    await loadView()
  } catch (error) {
    if (!handleUnauthorized(error)) connectionError.value = message(error)
  } finally {
    connectionBusy.value = false
  }
}

async function connectWithToken(): Promise<void> {
  if (connectionBusy.value) return
  if (!tokenDraft.value.trim() || /[\r\n]/.test(tokenDraft.value.trim())) {
    connectionError.value = '请粘贴单行访问凭证。'
    return
  }
  setToken(tokenDraft.value)
  tokenDraft.value = ''
  await connect()
}

function disconnect(): void {
  if (actionBusy.value) return
  clearSession()
  connectionError.value = ''
  needsToken.value = true
  authMessage.value = '已退出，访问凭证已清除。'
}

async function loadView(): Promise<void> {
  if (!context.value) return
  const version = ++loadVersion
  const session = sessionVersion
  const target = { ...route.value }
  const preserveQuantity = target.view === 'products' && product.value?.productId === target.id
  const current = () => version === loadVersion && session === sessionVersion && context.value !== null
  searchInput.value = target.keyword
  listBusy.value = true
  detailBusy.value = Boolean(target.id)
  listError.value = ''
  detailError.value = ''
  product.value = null
  order.value = null
  products.value = []
  orders.value = []
  if (!preserveQuantity) quantity.value = 1
  cancelConfirmation.value = false

  const listTask = (async () => {
    try {
      if (target.view === 'products') {
        const page = await api.products(target.keyword, target.page, PAGE_SIZE)
        if (current()) products.value = page.items
      } else {
        const page = await api.orders(target.page, PAGE_SIZE)
        if (current()) orders.value = page.items
      }
    } catch (error) {
      if (current() && !handleUnauthorized(error)) listError.value = message(error)
    } finally {
      if (current()) listBusy.value = false
    }
  })()

  const detailTask = (async () => {
    if (!target.id) return
    try {
      if (target.view === 'products') {
        const value = await api.product(target.id)
        if (current()) product.value = value
      } else {
        const value = await api.order(target.id)
        if (current()) order.value = value
      }
    } catch (error) {
      if (current() && !handleUnauthorized(error)) detailError.value = message(error)
    } finally {
      if (current()) {
        detailBusy.value = false
        await nextTick()
        detailHeading.value?.focus()
      }
    }
  })()
  await Promise.all([listTask, detailTask])
}

async function refresh(): Promise<void> {
  if (actionBusy.value) return
  actionError.value = ''
  await loadView()
}

async function routeChanged(): Promise<void> {
  const previousId = route.value.id
  route.value = readRoute()
  actionError.value = ''
  await loadView()
  if (previousId && !route.value.id) {
    await nextTick()
    listHeading.value?.focus()
  }
}

function search(): void {
  const next = routeHref(route.value, { view: 'products', id: null, page: 1, keyword: searchInput.value.trim() })
  if (window.location.hash === next) void refresh()
  else window.location.hash = next
}

async function createOrder(): Promise<void> {
  if (!product.value || !validQuantity.value || actionBusy.value) return
  const body = { productId: product.value.productId, quantity: Number(quantity.value) }
  const signature = JSON.stringify(body)
  const key = createAttempts.get(signature) ?? crypto.randomUUID()
  createAttempts.set(signature, key)
  const session = sessionVersion
  actionBusy.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const created = await api.createOrder(body, key)
    if (session !== sessionVersion) return
    createAttempts.delete(signature)
    uncertainCreate.value = ''
    notice.value = '订单已创建，库存已为你保留。请在付款时间内完成操作。'
    window.location.hash = routeHref(route.value, { view: 'orders', id: created.orderId, page: 1, keyword: '' })
  } catch (error) {
    if (session !== sessionVersion || handleUnauthorized(error)) return
    const uncertain = error instanceof ApiError && (error.status === 0 || error.status >= 500)
    if (uncertain) {
      uncertainCreate.value = signature
      actionError.value = '尚未确认订单是否创建。请重试本次提交，或到“我的订单”检查；请勿刷新页面后重复下单。'
    } else {
      createAttempts.delete(signature)
      actionError.value = message(error)
      if (error instanceof ApiError && error.status === 409) await loadView()
    }
  } finally {
    actionBusy.value = false
  }
}

async function changeOrder(action: 'pay' | 'cancel'): Promise<void> {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT' || actionBusy.value) return
  if (action === 'pay' && !context.value?.simulatePaymentEnabled) return
  const id = order.value.orderId
  const session = sessionVersion
  const key = paymentAttempts.get(id) ?? crypto.randomUUID()
  if (action === 'pay') paymentAttempts.set(id, key)
  actionBusy.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const changed = await (action === 'pay' ? api.simulatePayment(id, key) : api.cancelOrder(id))
    if (session !== sessionVersion) return
    paymentAttempts.delete(id)
    if (order.value?.orderId === id) order.value = changed
    orders.value = orders.value.map(item => item.orderId === id ? changed : item)
    cancelConfirmation.value = false
    notice.value = action === 'pay' ? '模拟付款已完成，未发生真实扣款。' : '订单已关闭，占用的库存已释放。'
  } catch (error) {
    if (session !== sessionVersion || handleUnauthorized(error)) return
    actionError.value = error instanceof ApiError && (error.status === 0 || error.status >= 500)
      ? '暂时没有收到结果。可以刷新订单确认，或重试本次操作。'
      : message(error)
    if (error instanceof ApiError && error.status === 409) await loadView()
  } finally {
    actionBusy.value = false
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && route.value.id && !actionBusy.value) {
    window.location.hash = listHref.value
  }
}

function skipToContent(): void {
  document.getElementById('main-content')?.focus()
}

onMounted(() => {
  if (window.self !== window.top) document.documentElement.classList.add('platform-embedded')
  window.addEventListener('hashchange', routeChanged)
  window.addEventListener('keydown', onKeydown)
  ticker = window.setInterval(() => { now.value = Date.now() }, 1000)
  void connect()
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', routeChanged)
  window.removeEventListener('keydown', onKeydown)
  window.clearInterval(ticker)
  setToken('')
})
</script>

<template>
  <a class="skip-link" href="#main-content" @click.prevent="skipToContent">跳到主要内容</a>
  <header class="topbar">
    <a class="brand" href="#/products" aria-label="ShopGuard 商品首页">
      <span class="brand-symbol" aria-hidden="true">SG</span>
      <span>ShopGuard<span class="brand-subtitle">校园交易</span></span>
    </a>
    <a class="support-link" href="/" target="_top">智能客服 <span aria-hidden="true">↗</span></a>
  </header>

  <main id="main-content" class="shell" tabindex="-1">
    <div class="page-heading">
      <div><p class="eyebrow">SHOPGUARD / TRADE</p><h1>从商品到订单。</h1></div>
      <div v-if="context" class="identity">
        <span v-if="context.mode === 'LOCAL_DEMO'" class="mode-badge">本地演示</span>
        <span>当前用户 <strong>{{ context.userId }}</strong></span>
        <button v-if="context.mode === 'JWT'" class="text-button" :disabled="actionBusy" @click="disconnect">退出连接</button>
      </div>
    </div>

    <div v-if="connectionBusy && !context" class="connection-state" role="status">
      <span class="loading-dot" aria-hidden="true"></span>正在连接交易服务…
    </div>

    <section v-else-if="!context" class="connection-panel" aria-labelledby="connection-title">
      <span class="section-mark" aria-hidden="true">↗</span>
      <div>
        <h2 id="connection-title">{{ needsToken ? '连接你的账号' : '暂时无法连接' }}</h2>
        <p v-if="needsToken">{{ authMessage }}</p>
        <p v-else>确认交易服务已启动后，再试一次。</p>
        <p v-if="connectionError" class="error-text" role="alert">{{ connectionError }}</p>
        <form v-if="needsToken" class="token-form" @submit.prevent="connectWithToken">
          <label for="access-token">访问凭证（Bearer Token）</label>
          <input id="access-token" v-model="tokenDraft" type="password" autocomplete="off" autocapitalize="none"
            spellcheck="false" placeholder="粘贴已有的访问凭证" :disabled="connectionBusy" required />
          <p class="hint">凭证仅在本次页面打开期间保留，刷新后需要重新连接。</p>
          <button class="primary" type="submit" :disabled="connectionBusy || !tokenDraft.trim()">
            {{ connectionBusy ? '连接中…' : '连接账号' }}
          </button>
        </form>
        <button v-else class="primary" :disabled="connectionBusy" @click="connect">{{ connectionBusy ? '连接中…' : '重新连接' }}</button>
      </div>
    </section>

    <template v-else>
      <p v-if="context.mode === 'LOCAL_DEMO'" class="demo-note">
        使用固定演示用户 {{ context.userId }}；商品与订单由交易服务保存。
        <span v-if="context.simulatePaymentEnabled">付款为模拟操作，不会真实扣款。</span>
      </p>
      <p v-if="context.mode === 'SESSION' && context.simulatePaymentEnabled" class="demo-note">当前启用了模拟付款；操作只更新本地交易记录，不会扣除真实资金。</p>

      <div class="navigation-row">
        <nav aria-label="交易导航">
          <a href="#/products" :aria-current="route.view === 'products' ? 'page' : undefined" :class="{ active: route.view === 'products' }">商品</a>
          <a href="#/orders" :aria-current="route.view === 'orders' ? 'page' : undefined" :class="{ active: route.view === 'orders' }">我的订单</a>
        </nav>
        <button class="refresh-button" :disabled="listBusy || detailBusy || actionBusy" @click="refresh"><span aria-hidden="true">↻</span> 刷新</button>
      </div>

      <div v-if="notice" class="notice" role="status"><span>{{ notice }}</span><button class="notice-close" aria-label="关闭提示" @click="notice = ''">×</button></div>
      <div class="workspace" :class="{ 'has-detail': route.id }">
        <section class="catalog" :aria-busy="listBusy" aria-labelledby="list-title">
          <div class="list-heading">
            <h2 id="list-title" ref="listHeading" tabindex="-1">{{ route.view === 'products' ? '在售商品' : '我的订单' }}</h2>
            <span class="list-page">第 {{ route.page }} 页</span>
          </div>

          <form v-if="route.view === 'products'" class="search-form" role="search" @submit.prevent="search">
            <label class="sr-only" for="product-search">搜索商品</label>
            <input id="product-search" v-model="searchInput" type="search" maxlength="100" placeholder="搜索商品名称" />
            <button class="primary" type="submit" :disabled="listBusy">搜索</button>
          </form>

          <div v-if="listBusy" class="empty-state" role="status"><span class="loading-dot" aria-hidden="true"></span><p>正在读取{{ route.view === 'products' ? '商品' : '订单' }}…</p></div>
          <div v-else-if="listError" class="empty-state error-state" role="alert"><h3>暂时无法读取</h3><p>{{ listError }}</p><button class="secondary" @click="refresh">重试</button></div>
          <template v-else-if="route.view === 'products'">
            <div v-if="!products.length" class="empty-state"><span class="empty-symbol" aria-hidden="true">—</span><h3>{{ route.keyword ? '没有找到相关商品' : '这一页暂无商品' }}</h3><p>{{ route.keyword ? '换一个关键词，再找找看。' : '稍后刷新，或返回上一页。' }}</p><a v-if="route.keyword" class="text-link" href="#/products">查看全部商品</a></div>
            <div v-else class="product-grid">
              <a v-for="item in products" :key="item.productId" class="product-item" :class="{ selected: route.id === item.productId }"
                :href="routeHref(route, { id: item.productId })" :aria-current="route.id === item.productId ? 'true' : undefined">
                <div class="product-top"><span class="product-number">#{{ item.productId }}</span><span :class="['stock', { 'sold-out': item.availableQuantity === 0 }]">{{ item.availableQuantity > 0 ? `可购 ${item.availableQuantity} 件` : '暂时售罄' }}</span></div>
                <h3>{{ item.title }}</h3>
                <p>{{ item.description || '查看商品详情' }}</p>
                <div class="product-bottom"><span class="price"><small>¥</small> {{ price(item.price) }}</span><span class="detail-link">查看 <span aria-hidden="true">↗</span></span></div>
              </a>
            </div>
          </template>
          <template v-else>
            <div v-if="!orders.length" class="empty-state"><span class="empty-symbol" aria-hidden="true">—</span><h3>{{ route.page === 1 ? '还没有订单' : '这一页暂无订单' }}</h3><p>创建的订单会保存在这里。</p><a class="text-link" href="#/products">去看看商品 <span aria-hidden="true">→</span></a></div>
            <div v-else class="order-list">
              <a v-for="item in orders" :key="item.orderId" class="order-item" :class="{ selected: route.id === item.orderId }"
                :href="routeHref(route, { id: item.orderId })" :aria-current="route.id === item.orderId ? 'true' : undefined">
                <div class="order-row"><span class="order-number">订单 {{ item.orderId }}</span><span :class="['status-badge', item.status.toLowerCase()]">{{ statusLabel(item.status) }}</span></div>
                <h3>{{ item.productName }}</h3>
                <div class="order-row"><span class="muted">{{ item.quantity }} 件 · {{ dateTime(item.createdAt) }}</span><span class="order-amount">¥ {{ price(item.totalAmount) }}</span></div>
              </a>
            </div>
          </template>

          <nav v-if="!listBusy && !listError && (route.page > 1 || hasNextPage)" class="pagination" aria-label="分页">
            <a v-if="route.page > 1" class="secondary" :href="routeHref(route, { page: route.page - 1, id: null })">← 上一页</a><span v-else></span>
            <a v-if="hasNextPage" class="secondary" :href="routeHref(route, { page: route.page + 1, id: null })">下一页 →</a>
          </nav>
        </section>

        <aside class="detail-panel" :aria-busy="detailBusy" :aria-label="route.view === 'products' ? '商品详情' : '订单详情'">
          <div class="detail-top"><span class="eyebrow">{{ route.view === 'products' ? '商品详情' : '订单详情' }}</span><a v-if="route.id" class="close-detail" :href="listHref" aria-label="关闭详情">×</a></div>
          <div v-if="!route.id" class="detail-placeholder"><span class="detail-symbol" aria-hidden="true">↗</span><h2 id="detail-title">{{ route.view === 'products' ? '选择一件商品' : '查看一笔订单' }}</h2><p>{{ route.view === 'products' ? '查看详情、选择数量，然后确认下单。' : '查看订单进度，或继续完成付款操作。' }}</p></div>
          <div v-else-if="detailBusy" class="empty-state" role="status"><p>正在读取详情…</p></div>
          <div v-else-if="detailError" class="detail-error" role="alert"><h2 id="detail-title" ref="detailHeading" tabindex="-1">无法读取详情</h2><p>{{ detailError }}</p><button class="secondary" @click="refresh">重试</button></div>
          <template v-else-if="product">
            <p class="product-number">商品 #{{ product.productId }}</p>
            <h2 id="detail-title" ref="detailHeading" class="detail-name" tabindex="-1">{{ product.title }}</h2>
            <p class="description">{{ product.description || '暂无补充描述。' }}</p>
            <div class="detail-price"><span class="price"><small>¥</small> {{ price(product.price) }}</span><span class="muted">可购 {{ product.availableQuantity }} 件</span></div>
            <form class="checkout-form" @submit.prevent="createOrder">
              <div class="quantity-row"><label for="quantity">购买数量</label><input id="quantity" v-model.number="quantity" inputmode="numeric" type="number" min="1" :max="quantityLimit" step="1" :disabled="actionBusy || product.availableQuantity === 0" required /></div>
              <p class="hint">单次最多购买 {{ MAX_QUANTITY }} 件，以可购数量为准。</p>
              <p v-if="product.availableQuantity > 0 && !validQuantity" class="error-text">请输入 1 到 {{ quantityLimit }} 之间的整数。</p>
              <div class="total-row"><span>订单金额</span><strong>¥ {{ subtotal }}</strong></div>
              <p class="checkout-note">下单后保留库存 {{ Math.ceil(context.paymentWindowSeconds / 60) }} 分钟，超时未付款将关闭订单并释放库存。</p>
              <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
              <button class="primary full-width" type="submit" :disabled="!validQuantity || actionBusy">
                {{ actionBusy ? '正在提交…' : product.availableQuantity === 0 ? '暂时售罄' : retryingCreate ? '重试本次提交' : '确认下单' }}
              </button>
              <a v-if="retryingCreate" class="text-link check-orders" href="#/orders">先检查我的订单 →</a>
            </form>
          </template>
          <template v-else-if="order">
            <span :class="['status-badge', order.status.toLowerCase()]">{{ statusLabel(order.status) }}</span>
            <h2 id="detail-title" ref="detailHeading" class="detail-name" tabindex="-1">{{ order.productName }}</h2>
            <p class="order-id">订单 {{ order.orderId }}</p>
            <dl class="order-facts"><div><dt>购买数量</dt><dd>{{ order.quantity }} 件</dd></div><div><dt>订单金额</dt><dd class="amount">¥ {{ price(order.totalAmount) }}</dd></div><div><dt>创建时间</dt><dd>{{ dateTime(order.createdAt) }}</dd></div><div v-if="order.status === 'PENDING_PAYMENT'"><dt>付款截止</dt><dd>{{ dateTime(order.expiresAt) }}</dd></div></dl>
            <div v-if="order.status === 'PENDING_PAYMENT'" class="payment-area">
              <p class="payment-time">{{ remainingTime }}</p>
              <p v-if="context.simulatePaymentEnabled" class="hint">模拟付款仅用于验证订单流程，不会发生真实扣款。</p>
              <p v-else class="hint">当前未开放在线付款，可保留订单或取消。</p>
              <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
              <template v-if="!cancelConfirmation">
                <button v-if="context.simulatePaymentEnabled" class="primary full-width" :disabled="actionBusy" @click="changeOrder('pay')">{{ actionBusy ? '正在处理…' : '模拟付款' }}</button>
                <button class="secondary full-width cancel-button" :disabled="actionBusy" @click="cancelConfirmation = true">取消订单</button>
              </template>
              <div v-else class="cancel-confirmation"><p>确认取消这笔订单？占用的库存将被释放。</p><div class="confirmation-actions"><button class="danger" :disabled="actionBusy" @click="changeOrder('cancel')">{{ actionBusy ? '正在取消…' : '确认取消' }}</button><button class="secondary" :disabled="actionBusy" @click="cancelConfirmation = false">保留订单</button></div></div>
            </div>
            <div v-else class="closed-note"><p>{{ order.status === 'PAID' ? '付款已完成。' : '订单已关闭，不再占用库存。' }}</p><p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p><a class="text-link" href="#/products">继续浏览商品 →</a></div>
          </template>
        </aside>
      </div>
    </template>
    <footer><span>ShopGuard</span><span>订单状态与金额以服务端结果为准。</span></footer>
  </main>
</template>
