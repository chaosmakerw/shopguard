<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  Search,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ReceiptText,
  ArrowUpRight,
  X,
  RotateCcw,
} from "@lucide/vue";
import { request } from "../api";
import {
  money,
  date,
  labels,
  returnLabels,
  errorText,
  type ManagedOrder,
  type PageResult,
} from "../commerce";
const emit = defineEmits<{ returns: [] }>();
const data = ref<PageResult<ManagedOrder>>({
    items: [],
    total: 0,
    page: 1,
    size: 12,
  }),
  query = ref(""),
  status = ref("all"),
  page = ref(1),
  loading = ref(false),
  error = ref(""),
  selected = ref<ManagedOrder | null>(null);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    data.value = await request(
      "/admin/commerce/orders?" +
        new URLSearchParams({
          keyword: query.value,
          status: status.value,
          page: String(page.value),
          size: "12",
        }),
    );
  } catch (e) {
    error.value = errorText(e);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="content-page">
    <header class="page-heading">
      <div>
        <h1>订单管理</h1>
      </div>
      <button class="secondary" :disabled="loading" @click="load">
        <RefreshCw :size="15" />刷新订单
      </button>
    </header>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div>
    <section class="surface-panel">
      <div class="management-toolbar">
        <form
          class="search-field"
          @submit.prevent="
            page = 1;
            load();
          "
        >
          <Search :size="16" /><input
            v-model="query"
            aria-label="搜索全部订单"
            placeholder="搜索订单编号或商品名称"
          /><button>搜索</button>
        </form>
        <select
          v-model="status"
          aria-label="订单状态"
          @change="
            page = 1;
            load();
          "
        >
          <option value="all">全部订单</option>
          <option
            v-for="key in ['PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED']"
            :key="key"
            :value="key"
          >
            {{ labels[key] }}
          </option></select
        ><span class="push-right muted">共 {{ data.total }} 笔订单</span>
      </div>
      <div class="table-scroll">
        <table class="business-table">
          <thead>
            <tr>
              <th>订单 / 商品</th>
              <th>买家</th>
              <th>金额 / 数量</th>
              <th>订单状态</th>
              <th>售后</th>
              <th>创建时间</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in data.items" :key="order.orderId">
              <td>
                <strong>{{ order.productName }}</strong
                ><small class="mono">{{ order.orderId }}</small>
              </td>
              <td>
                {{ order.buyerName }}<small>#{{ order.buyerId }}</small>
              </td>
              <td>
                <strong>¥ {{ money(order.totalAmount) }}</strong
                ><small>{{ order.quantity }} 件商品</small>
              </td>
              <td>
                <span
                  class="status-label"
                  :class="order.status.toLowerCase()"
                  >{{ labels[order.status] || order.status }}</span
                >
              </td>
              <td>
                <span
                  v-if="order.returnStatus"
                  class="status-label"
                  :class="order.returnStatus.toLowerCase()"
                  >{{ returnLabels[order.returnStatus] }}</span
                ><span v-else class="muted">—</span>
              </td>
              <td>{{ date(order.createdAt) }}</td>
              <td>
                <button
                  class="row-detail"
                  :aria-label="'查看订单 ' + order.orderId"
                  @click="selected = order"
                >
                  详情<ArrowUpRight :size="14" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!data.items.length" class="empty-panel">
          <ReceiptText :size="32" />
          <h3>{{ loading ? "正在读取订单" : "没有匹配的订单" }}</h3>
          <p>新订单会自动出现在这里。</p>
        </div>
      </div>
      <footer class="table-pagination">
        <span>支付与退款均为本地模拟，不涉及真实资金</span>
        <div>
          <button
            class="icon-button"
            aria-label="订单上一页"
            :disabled="page === 1 || loading"
            @click="
              page--;
              load();
            "
          >
            <ChevronLeft :size="17" /></button
          ><span
            >{{ page }} / {{ Math.max(1, Math.ceil(data.total / 12)) }}</span
          ><button
            class="icon-button"
            aria-label="订单下一页"
            :disabled="page * 12 >= data.total || loading"
            @click="
              page++;
              load();
            "
          >
            <ChevronRight :size="17" />
          </button>
        </div>
      </footer>
    </section>
    <div
      v-if="selected"
      class="commerce-modal-backdrop"
      @click.self="selected = null"
    >
      <section
        class="commerce-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="managed-order-title"
      >
        <header>
          <div>
            <span class="section-kicker">订单详情</span>
            <h2 id="managed-order-title">{{ selected.productName }}</h2>
          </div>
          <button
            class="icon-button"
            aria-label="关闭订单信息"
            @click="selected = null"
          >
            <X :size="20" />
          </button>
        </header>
        <span class="status-label" :class="selected.status.toLowerCase()">{{
          labels[selected.status]
        }}</span>
        <dl class="record-fields">
          <div>
            <dt>订单编号</dt>
            <dd class="mono">{{ selected.orderId }}</dd>
          </div>
          <div>
            <dt>买家</dt>
            <dd>{{ selected.buyerName }} · {{ selected.buyerId }}</dd>
          </div>
          <div>
            <dt>成交金额</dt>
            <dd class="amount">¥ {{ money(selected.totalAmount) }}</dd>
          </div>
          <div>
            <dt>数量</dt>
            <dd>{{ selected.quantity }} 件</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ date(selected.createdAt) }}</dd>
          </div>
          <div>
            <dt>售后进度</dt>
            <dd>
              {{
                selected.returnStatus
                  ? returnLabels[selected.returnStatus]
                  : "未申请售后"
              }}
            </dd>
          </div>
        </dl>
        <footer>
          <button
            v-if="selected.returnStatus"
            class="primary"
            @click="emit('returns')"
          >
            <RotateCcw :size="16" />进入售后中心</button
          ><button class="secondary" @click="selected = null">关闭</button>
        </footer>
      </section>
    </div>
  </div>
</template>
