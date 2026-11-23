<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  ArrowUpRight,
  ArrowRight,
  RefreshCw,
  Package,
  ReceiptText,
  RotateCcw,
  Wallet,
  Database,
  Check,
  Clock3,
  AlertCircle,
  Activity,
} from "@lucide/vue";
import { request } from "../api";
import { money, date, labels, errorText, type ManagedOrder } from "../commerce";
type Overview = {
  products: number;
  orders: number;
  paidAmount: string;
  refundAmount: string;
  pendingReturns: number;
  lowStock: number;
  inventory: { available: number; reserved: number; sold: number };
  days: { date: string; orders: number; amount: string }[];
  statuses: { status: string; count: number }[];
  recentOrders: ManagedOrder[];
  activity: {
    entityType: string;
    entityId: string;
    action: string;
    summary: string;
    createdAt: number;
  }[];
  database: { engine: string; connected: boolean; persistent: boolean };
};
const emit = defineEmits<{
  navigate: [
    page:
      | "catalog"
      | "operations"
      | "aftersales"
      | "database"
      | "trade"
      | "gateway",
  ];
}>();
const data = ref<Overview>(),
  loading = ref(false),
  error = ref(""),
  updated = ref("");
const max = computed(() =>
  Math.max(1, ...(data.value?.days.map((day) => day.orders) || [])),
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    data.value = await request("/admin/commerce/overview");
    updated.value = new Date().toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch (e) {
    error.value = errorText(e);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="content-page overview-page">
    <header class="page-heading">
      <div>
        <h1>经营概览</h1>
      </div>
      <button class="secondary" :disabled="loading" @click="load">
        <RefreshCw :size="15" :class="{ spin: loading }" />刷新数据
      </button>
    </header>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div>
    <div v-if="!data" class="empty-panel">
      <RefreshCw v-if="loading" class="spin" />{{
        loading ? "正在读取经营数据…" : "暂时无法加载数据"
      }}
    </div>
    <template v-else>
      <div class="overview-status">
        <span
          ><span class="live-dot"></span>{{ data.database.engine }} 已连接</span
        ><span
          ><Check :size="13" />{{
            data.database.persistent ? "业务数据持久保存" : "测试数据库"
          }}</span
        ><span class="push-right">更新于 {{ updated }}</span>
      </div>
      <div class="commerce-metrics">
        <article class="metric-tile featured">
          <div><span>当前成交金额</span><Wallet :size="19" /></div>
          <strong><small>¥</small>{{ money(data.paidAmount) }}</strong>
          <p>已支付订单合计 · 模拟支付</p>
        </article>
        <article class="metric-tile">
          <div><span>全部订单</span><ReceiptText :size="19" /></div>
          <strong>{{ data.orders }}<small>笔</small></strong
          ><button @click="emit('navigate', 'operations')">
            查看订单<ArrowUpRight :size="14" />
          </button>
        </article>
        <article class="metric-tile">
          <div><span>在售商品</span><Package :size="19" /></div>
          <strong>{{ data.products }}<small>件商品</small></strong
          ><button @click="emit('navigate', 'catalog')">
            管理商品<ArrowUpRight :size="14" />
          </button>
        </article>
        <article class="metric-tile">
          <div><span>待处理售后</span><RotateCcw :size="19" /></div>
          <strong>{{ data.pendingReturns }}<small>笔</small></strong
          ><button @click="emit('navigate', 'aftersales')">
            进入售后中心<ArrowUpRight :size="14" />
          </button>
        </article>
      </div>
      <div class="overview-middle">
        <section class="surface-panel order-trend">
          <header>
            <div>
              <h2>近七日订单</h2>
              <p>按订单创建日期统计</p>
            </div>
            <span class="chart-legend"><i></i>订单数</span>
          </header>
          <div
            class="bar-chart"
            role="img"
            :aria-label="
              data.days
                .map((day) => day.date + '：' + day.orders + '笔')
                .join('，')
            "
          >
            <div class="chart-grid"><i></i><i></i><i></i><i></i></div>
            <div v-for="day in data.days" :key="day.date" class="day-bar">
              <div class="bar-track">
                <span class="bar-value">{{ day.orders }}</span
                ><i
                  :style="{
                    height: day.orders
                      ? Math.max(4, (day.orders / max) * 100) + '%'
                      : '0',
                  }"
                ></i>
              </div>
              <span>{{ day.date.slice(5).replace("-", "/") }}</span>
            </div>
          </div>
          <div class="chart-footer">
            <span
              ><Clock3 :size="14" />最近七天新增
              {{ data.days.reduce((sum, d) => sum + d.orders, 0) }} 笔订单</span
            ><button @click="emit('navigate', 'operations')">
              查看全部<ArrowRight :size="14" />
            </button>
          </div>
        </section>
        <section class="surface-panel work-queue">
          <header>
            <div>
              <h2>今天需要关注</h2>
              <p>从待办直接进入处理页面</p>
            </div>
            <Activity :size="19" />
          </header>
          <button @click="emit('navigate', 'aftersales')">
            <span class="queue-icon lavender"><RotateCcw :size="20" /></span
            ><span
              ><strong>售后审核与收货</strong
              ><small>确认申请、核对退回商品</small></span
            ><b>{{ data.pendingReturns }}</b
            ><ArrowUpRight :size="16" /></button
          ><button @click="emit('navigate', 'catalog')">
            <span class="queue-icon amber"><AlertCircle :size="20" /></span
            ><span
              ><strong>低库存商品</strong
              ><small>可售库存不超过 3 件</small></span
            ><b>{{ data.lowStock }}</b
            ><ArrowUpRight :size="16" />
          </button>
          <div class="inventory-summary">
            <span>库存分布</span>
            <div>
              <p>
                <b>{{ data.inventory.available }}</b
                >可售
              </p>
              <p>
                <b>{{ data.inventory.reserved }}</b
                >预占
              </p>
              <p>
                <b>{{ data.inventory.sold }}</b
                >已售
              </p>
            </div>
          </div>
        </section>
      </div>
      <div class="overview-bottom">
        <section class="surface-panel recent-orders">
          <header>
            <h2>最近订单</h2>
            <button class="text-button" @click="emit('navigate', 'operations')">
              全部订单<ArrowRight :size="14" />
            </button>
          </header>
          <div class="table-scroll">
            <table class="business-table">
              <thead>
                <tr>
                  <th>商品 / 订单</th>
                  <th>买家</th>
                  <th>金额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="order in data.recentOrders"
                  :key="order.orderId"
                  @click="emit('navigate', 'operations')"
                >
                  <td>
                    <strong>{{ order.productName }}</strong
                    ><small>{{ order.orderId }}</small>
                  </td>
                  <td>{{ order.buyerName }}</td>
                  <td class="numeric">¥ {{ money(order.totalAmount) }}</td>
                  <td>
                    <span
                      class="status-label"
                      :class="order.status.toLowerCase()"
                      >{{ labels[order.status] || order.status }}</span
                    >
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!data.recentOrders.length" class="empty-inline">
              还没有订单，前往商品页完成第一笔交易。
            </p>
          </div>
        </section>
        <section class="surface-panel activity-panel">
          <header>
            <h2>最近操作</h2>
            <span class="subtle-count">{{ data.activity.length }}</span>
          </header>
          <div v-if="data.activity.length" class="activity-list">
            <div v-for="(entry, index) in data.activity" :key="index">
              <i></i>
              <p>
                <strong>{{ entry.summary }}</strong
                ><small>{{ date(entry.createdAt) }}</small>
              </p>
            </div>
          </div>
          <div v-else class="quiet-empty">
            <Activity :size="30" />
            <p>商品维护与售后操作<br />会自动记录在这里</p>
          </div>
          <button class="panel-link" @click="emit('navigate', 'database')">
            <Database :size="15" />查看持久化数据<ArrowUpRight :size="15" />
          </button>
        </section>
      </div>
    </template>
  </div>
</template>
