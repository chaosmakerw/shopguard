<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import {
  RotateCcw,
  Check,
  X,
  ArrowUpRight,
  RefreshCw,
  PackageCheck,
  Truck,
  ChevronRight,
  ChevronLeft,
  ShieldCheck,
  MessageSquare,
} from "@lucide/vue";
import { post, request } from "../api";
import { api as tradeApi } from "../../../trade-web/src/api";
import type { TradeOrder } from "../../../trade-web/src/types";
import {
  date,
  money,
  returnLabels,
  errorText,
  type ReturnCase,
  type ReturnEvent,
  type PageResult,
} from "../commerce";
const props = defineProps<{ admin: boolean; orderId?: string }>();
const emit = defineEmits<{
  order: [id: string];
  ask: [id: string];
  consumed: [];
}>();
const management = ref(props.admin && !props.orderId),
  status = ref("all"),
  page = ref(1),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const data = ref<PageResult<ReturnCase>>({
    items: [],
    total: 0,
    page: 1,
    size: 12,
  }),
  selected = ref<ReturnCase | null>(null),
  events = ref<ReturnEvent[]>([]),
  order = ref<TradeOrder | null>(null),
  showForm = ref(false);
const form = ref({ type: "REFUND_ONLY", reason: "不再需要", note: "" }),
  note = ref(""),
  carrier = ref(""),
  tracking = ref(""),
  confirmAction = ref("");
const prefix = computed(() =>
  management.value ? "/admin/commerce/returns" : "/commerce/returns",
);
const actionLabels: Record<string, string> = {
  approve: "确认通过申请",
  reject: "填写驳回原因",
  receive: "确认收货并退款",
  withdraw: "撤销这笔申请",
  ship: "登记寄回物流",
};
const stage = computed(() =>
  selected.value
    ? ["REQUESTED", "APPROVED", "RETURNED", "REFUNDED"].indexOf(
        selected.value.status,
      )
    : 0,
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    data.value = await request(
      prefix.value +
        "?" +
        new URLSearchParams({
          status: status.value,
          page: String(page.value),
          size: "12",
        }),
    );
    if (selected.value) {
      const detail = await request<{ item: ReturnCase; events: ReturnEvent[] }>(
        prefix.value + "/" + selected.value.id,
      );
      selected.value = detail.item;
      events.value = detail.events;
    }
  } catch (e) {
    error.value = errorText(e);
  } finally {
    loading.value = false;
  }
}
async function open(item: ReturnCase) {
  error.value = "";
  confirmAction.value = "";
  note.value = "";
  carrier.value = "";
  tracking.value = "";
  try {
    const detail = await request<{ item: ReturnCase; events: ReturnEvent[] }>(
      prefix.value + "/" + item.id,
    );
    selected.value = detail.item;
    events.value = detail.events;
  } catch (e) {
    error.value = errorText(e);
  }
}
async function newRequest(id: string) {
  management.value = false;
  selected.value = null;
  await load();
  try {
    order.value = await tradeApi.order(id);
    const existing = await request<Partial<ReturnCase>>(
      "/commerce/orders/" + id + "/return",
    );
    if (
      existing.id &&
      !["REJECTED", "CANCELLED"].includes(existing.status || "")
    ) {
      await open(existing as ReturnCase);
    } else {
      form.value = { type: "REFUND_ONLY", reason: "不再需要", note: "" };
      showForm.value = true;
    }
  } catch (e) {
    error.value = errorText(e);
  } finally {
    emit("consumed");
  }
}
async function submit() {
  if (!order.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const item = await post<ReturnCase>(
      "/commerce/orders/" + order.value.orderId + "/return",
      form.value,
    );
    showForm.value = false;
    notice.value = "售后申请已提交，请等待审核";
    await load();
    await open(item);
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
async function act() {
  if (!selected.value || busy.value || !confirmAction.value) return;
  busy.value = true;
  error.value = "";
  try {
    const item = await post<ReturnCase>(
      prefix.value +
        "/" +
        selected.value.id +
        (management.value ? "/review" : "/action"),
      management.value
        ? { action: confirmAction.value, note: note.value }
        : {
            action: confirmAction.value,
            carrier: carrier.value,
            trackingNumber: tracking.value,
          },
    );
    notice.value = "售后状态已更新";
    await load();
    await open(item);
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
async function switchScope(value: boolean) {
  management.value = value;
  selected.value = null;
  page.value = 1;
  status.value = "all";
  await load();
}
watch(
  () => props.orderId,
  (id) => {
    if (id) void newRequest(id);
  },
);
onMounted(async () => {
  if (props.orderId) await newRequest(props.orderId);
  else await load();
});
</script>
<template>
  <div class="content-page aftersales-page">
    <header class="page-heading">
      <div>
        <h1>售后中心</h1>
      </div>
      <button class="secondary" :disabled="loading" @click="load">
        <RefreshCw :size="15" />刷新进度
      </button>
    </header>
    <div
      v-if="error && !showForm && !confirmAction"
      class="alert error"
      role="alert"
    >
      {{ error }}
    </div>
    <div v-if="notice" class="alert success" role="status">
      {{ notice
      }}<button aria-label="关闭售后提示" @click="notice = ''">
        <X :size="16" />
      </button>
    </div>
    <div class="aftersales-toolbar">
      <div v-if="admin" class="segmented">
        <button :class="{ active: management }" @click="switchScope(true)">
          售后审核</button
        ><button :class="{ active: !management }" @click="switchScope(false)">
          我的申请
        </button>
      </div>
      <div v-else class="section-kicker">我的售后申请</div>
      <select
        v-model="status"
        aria-label="售后状态"
        @change="
          page = 1;
          load();
        "
      >
        <option value="all">全部状态</option>
        <option
          v-for="key in [
            'REQUESTED',
            'APPROVED',
            'RETURNED',
            'REFUNDED',
            'REJECTED',
            'CANCELLED',
          ]"
          :key="key"
          :value="key"
        >
          {{ returnLabels[key] }}
        </option>
      </select>
    </div>
    <div class="aftersales-layout" :class="{ 'has-selected': selected }">
      <section class="surface-panel">
        <div class="table-scroll">
          <table class="business-table return-table">
            <thead>
              <tr>
                <th>售后商品</th>
                <th>申请金额</th>
                <th>进度</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in data.items"
                :key="item.id"
                :class="{ selected: selected?.id === item.id }"
                @click="open(item)"
              >
                <td>
                  <strong>{{ item.productName }}</strong
                  ><small
                    >{{ item.type === "REFUND_ONLY" ? "仅退款" : "退货退款" }} ·
                    {{ item.reason }}</small
                  ><small>{{ date(item.createdAt) }}</small>
                </td>
                <td class="numeric">¥ {{ money(item.amount) }}</td>
                <td>
                  <span
                    class="status-label"
                    :class="item.status.toLowerCase()"
                    >{{ returnLabels[item.status] }}</span
                  >
                </td>
                <td>
                  <button
                    class="icon-button"
                    :aria-label="'查看售后 ' + item.id"
                    @click.stop="open(item)"
                  >
                    <ChevronRight :size="17" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!data.items.length" class="empty-panel">
            <RotateCcw :size="36" />
            <h3>
              {{
                loading
                  ? "正在读取申请"
                  : management
                    ? "暂时没有售后待办"
                    : "还没有售后申请"
              }}
            </h3>
            <p>
              {{
                management
                  ? "客户提交申请后，可在这里审核和处理。"
                  : "打开一笔已支付订单，即可申请售后。"
              }}
            </p>
            <button
              v-if="!management"
              class="secondary"
              @click="emit('order', '')"
            >
              前往我的订单<ArrowUpRight :size="14" />
            </button>
          </div>
        </div>
        <footer class="table-pagination">
          <span>共 {{ data.total }} 笔申请</span>
          <div>
            <button
              class="icon-button"
              aria-label="售后上一页"
              :disabled="page === 1 || loading"
              @click="
                page--;
                load();
              "
            >
              <ChevronLeft :size="17" /></button
            ><span>{{ page }}</span
            ><button
              class="icon-button"
              aria-label="售后下一页"
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
      <aside v-if="selected" class="surface-panel return-detail">
        <header>
          <div>
            <span class="section-kicker">售后详情</span>
            <h2>{{ selected.productName }}</h2>
          </div>
          <button
            class="icon-button"
            aria-label="关闭售后详情"
            @click="selected = null"
          >
            <X :size="18" />
          </button>
        </header>
        <div class="return-status-line">
          <span class="status-label" :class="selected.status.toLowerCase()">{{
            returnLabels[selected.status]
          }}</span
          ><strong>¥ {{ money(selected.amount) }}</strong>
        </div>
        <div class="return-stepper">
          <div
            v-for="(label, index) in selected.type === 'REFUND_ONLY'
              ? ['提交申请', '审核退款']
              : ['提交申请', '审核通过', '登记寄回', '收货退款']"
            :key="index"
            :class="{
              done:
                selected.status === 'REFUNDED' ||
                (selected.type === 'REFUND_ONLY'
                  ? index === 0
                  : index <= stage),
            }"
          >
            <span
              ><Check
                v-if="selected.status === 'REFUNDED' || index < stage"
                :size="12"
              /><span v-else>{{ index + 1 }}</span></span
            ><small>{{ label }}</small>
          </div>
        </div>
        <dl class="record-fields">
          <div>
            <dt>订单编号</dt>
            <dd class="mono">{{ selected.orderId }}</dd>
          </div>
          <div>
            <dt>售后类型</dt>
            <dd>
              {{ selected.type === "REFUND_ONLY" ? "仅退款" : "退货退款" }}
            </dd>
          </div>
          <div>
            <dt>申请原因</dt>
            <dd>{{ selected.reason }}</dd>
          </div>
          <div>
            <dt>商品数量</dt>
            <dd>{{ selected.quantity }} 件</dd>
          </div>
        </dl>
        <div class="return-description">
          <span>申请说明</span>
          <p>{{ selected.note }}</p>
        </div>
        <div v-if="selected.carrier" class="shipping-box">
          <Truck :size="18" />
          <div>
            <strong>{{ selected.carrier }}</strong
            ><span>{{ selected.trackingNumber }}</span>
          </div>
        </div>
        <div v-if="selected.decisionNote" class="decision-note">
          <ShieldCheck :size="16" />
          <p>{{ selected.decisionNote }}</p>
        </div>
        <div v-if="confirmAction" class="return-action-form">
          <div v-if="error" class="alert error" role="alert">{{ error }}</div>
          <h3>{{ actionLabels[confirmAction] }}</h3>
          <p
            v-if="
              confirmAction === 'approve' && selected.type === 'REFUND_ONLY'
            "
          >
            通过后将完成模拟退款，并归还库存。
          </p>
          <p v-if="confirmAction === 'receive'">
            确认收到商品后，将完成模拟退款并归还库存。
          </p>
          <form @submit.prevent="act">
            <template v-if="confirmAction === 'ship'"
              ><label
                >物流公司<input
                  v-model="carrier"
                  maxlength="40"
                  required
                  placeholder="例如：顺丰速运" /></label
              ><label
                >运单号<input
                  v-model="tracking"
                  pattern="[A-Za-z0-9-]{4,80}"
                  maxlength="80"
                  required /></label></template
            ><label v-else-if="management"
              >{{ confirmAction === "reject" ? "驳回原因" : "处理备注"
              }}<textarea
                v-model="note"
                rows="3"
                maxlength="500"
                :required="confirmAction === 'reject'"
                :placeholder="
                  confirmAction === 'reject'
                    ? '说明需要补充的信息或不符合的条件'
                    : '选填，记录本次处理说明'
                "
              ></textarea>
            </label>
            <div class="button-row">
              <button
                type="button"
                class="secondary"
                :disabled="busy"
                @click="
                  confirmAction = '';
                  error = '';
                "
              >
                返回</button
              ><button class="primary" :disabled="busy">
                {{ busy ? "处理中…" : "确认提交" }}
              </button>
            </div>
          </form>
        </div>
        <div
          v-else-if="
            !management || ['REQUESTED', 'RETURNED'].includes(selected.status)
          "
          class="return-actions"
        >
          <template v-if="management && selected.status === 'REQUESTED'"
            ><button class="primary" @click="confirmAction = 'approve'">
              <Check :size="16" />通过申请</button
            ><button class="secondary" @click="confirmAction = 'reject'">
              驳回申请
            </button></template
          ><button
            v-if="management && selected.status === 'RETURNED'"
            class="primary"
            @click="confirmAction = 'receive'"
          >
            <PackageCheck :size="17" />确认收货并退款</button
          ><button
            v-if="!management && selected.status === 'APPROVED'"
            class="primary"
            @click="confirmAction = 'ship'"
          >
            <Truck :size="17" />登记寄回</button
          ><button
            v-if="!management && selected.status === 'REQUESTED'"
            class="secondary"
            @click="confirmAction = 'withdraw'"
          >
            撤销申请</button
          ><button
            v-if="!management"
            class="text-button"
            @click="emit('ask', selected.orderId)"
          >
            <MessageSquare :size="15" />让 AI 解释当前进度
          </button>
        </div>
        <section class="return-history">
          <h3>处理记录</h3>
          <div
            v-for="(entry, index) in events"
            :key="index"
            class="history-event"
          >
            <span><Check :size="12" /></span>
            <div>
              <strong>{{ returnLabels[entry.toStatus] }}</strong>
              <p>{{ entry.note }}</p>
              <small
                >{{ entry.actorRole === "ADMIN" ? "管理员" : "用户" }} ·
                {{ date(entry.createdAt) }}</small
              >
            </div>
          </div>
        </section>
        <p class="form-note">退款为本地模拟，未接入真实资金与物流服务。</p>
      </aside>
    </div>
    <div
      v-if="showForm && order"
      class="commerce-modal-backdrop"
      @click.self="!busy && (showForm = false)"
    >
      <section
        class="commerce-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="return-form-title"
      >
        <header>
          <div>
            <span class="section-kicker">订单售后</span>
            <h2 id="return-form-title">申请售后</h2>
          </div>
          <button
            class="icon-button"
            aria-label="关闭售后申请"
            :disabled="busy"
            @click="showForm = false"
          >
            <X :size="20" />
          </button>
        </header>
        <div class="return-order-summary">
          <PackageCheck :size="26" />
          <div>
            <strong>{{ order.productName }}</strong
            ><small>{{ order.quantity }} 件 · 订单 {{ order.orderId }}</small>
          </div>
          <b>¥ {{ money(order.totalAmount) }}</b>
        </div>
        <form @submit.prevent="submit">
          <div v-if="error" class="alert error" role="alert">{{ error }}</div>
          <div class="return-type-picker">
            <label :class="{ selected: form.type === 'REFUND_ONLY' }"
              ><input
                v-model="form.type"
                type="radio"
                value="REFUND_ONLY"
              /><strong>仅退款</strong
              ><small>无需寄回，由管理员审核</small></label
            ><label :class="{ selected: form.type === 'RETURN_REFUND' }"
              ><input
                v-model="form.type"
                type="radio"
                value="RETURN_REFUND"
              /><strong>退货退款</strong
              ><small>审核通过后登记寄回</small></label
            >
          </div>
          <label
            >售后原因<select v-model="form.reason" aria-label="售后原因">
              <option
                v-for="reason in [
                  '不再需要',
                  '商品与描述不符',
                  '质量问题',
                  '其他原因',
                ]"
                :key="reason"
              >
                {{ reason }}
              </option>
            </select></label
          ><label
            >情况说明<textarea
              v-model="form.note"
              rows="4"
              required
              maxlength="500"
              placeholder="说明商品情况和你的诉求，方便管理员处理"
            ></textarea>
          </label>
          <p class="form-note">
            本次支持整单售后；金额以订单成交快照为准。退款为本地模拟。
          </p>
          <footer>
            <button
              type="button"
              class="secondary"
              :disabled="busy"
              @click="showForm = false"
            >
              取消</button
            ><button class="primary" :disabled="busy">
              {{ busy ? "提交中…" : "提交售后申请" }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
