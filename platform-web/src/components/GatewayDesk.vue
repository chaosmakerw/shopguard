<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Activity,
  Check,
  Clock3,
  Cpu,
  RefreshCw,
  Search,
  ShieldCheck,
  Layers3,
  X,
  CircleAlert,
  Network,
  Database,
  Workflow,
} from "@lucide/vue";
import { request, type GatewayCall } from "../api";
const props = defineProps<{ traceId?: string }>();
const data = ref<{
  items: GatewayCall[];
  durable: boolean;
  capacity: number | null;
  windowRequests: number;
  windowSeconds: number;
  dailyRequests: number;
  streamConcurrency: number;
  streamRetry: boolean;
  streamCircuitBreaker: boolean;
} | null>(null);
const health = ref<Record<string, unknown>>(),
  loading = ref(false),
  error = ref(""),
  filter = ref(""),
  status = ref("all"),
  selected = ref<GatewayCall | null>(null);
const calls = computed(() => data.value?.items || []);
const visible = computed(() =>
  calls.value.filter(
    (c) =>
      (status.value === "all" || c.status === status.value) &&
      (!filter.value ||
        [c.requestId, c.traceId, c.model, c.kind].some((v) =>
          v.toLowerCase().includes(filter.value.toLowerCase()),
        )),
  ),
);
const attempts = computed(() =>
  calls.value.reduce((sum, c) => sum + c.attempts, 0),
);
const totalTokens = computed(() =>
  calls.value.reduce(
    (sum, c) => sum + (c.inputTokens ?? 0) + (c.outputTokens ?? 0),
    0,
  ),
);
const missing = computed(
  () =>
    calls.value.filter((c) => c.attempts > 0 && c.inputTokens === null).length,
);
const average = computed(() =>
  calls.value.length
    ? (
        calls.value.reduce((sum, c) => sum + c.durationMs, 0) /
        calls.value.length /
        1000
      ).toFixed(2)
    : "—",
);
const gateway = computed(
  () =>
    health.value?.gateway as
      | {
          mode?: string;
          model?: string;
          available?: boolean;
          eventsEnabled?: boolean;
          eventsAcknowledged?: number;
          eventsPersisted?: number;
          consumerState?: string;
        }
      | undefined,
);
const displayDate = (n: number) =>
  new Date(n).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
const ms = (n: number | null) =>
  n === null ? "—" : n < 1000 ? `${n} ms` : `${(n / 1000).toFixed(2)} s`;
async function load() {
  loading.value = true;
  error.value = "";
  try {
    [data.value, health.value] = await Promise.all([
      request<NonNullable<typeof data.value>>("/admin/gateway/requests"),
      request<Record<string, unknown>>("/admin/health"),
    ]);
    if (selected.value)
      selected.value =
        calls.value.find((c) => c.requestId === selected.value?.requestId) ||
        null;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "无法连接网关";
  } finally {
    loading.value = false;
  }
}
watch(
  () => props.traceId,
  (id) => {
    filter.value = id || "";
    selected.value = null;
  },
);
onMounted(() => {
  filter.value = props.traceId || "";
  void load();
});
</script>
<template>
  <div class="gateway-desk content-page">
    <header class="page-heading">
      <div>
        <div class="overline">LLM GATEWAY / OPERATIONS</div>
        <h1>模型调用控制台</h1>
        <p>从业务请求，看到每一次真实的模型调用。</p>
      </div>
      <button class="secondary" :disabled="loading" @click="load">
        <RefreshCw :size="15" :class="{ spin: loading }" />刷新
      </button>
    </header>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div>
    <div class="gateway-route">
      <div class="route-end">
        <span class="route-icon"><Layers3 :size="20" /></span>
        <div><strong>ShopGuard</strong><small>业务应用</small></div>
      </div>
      <div class="route-wire"><span>应用 Key</span><i></i></div>
      <div class="route-center">
        <ShieldCheck :size="22" />
        <div>
          <strong>LLM Gateway</strong><small>准入 · 配额 · 流式转发</small>
        </div>
      </div>
      <div class="route-wire"><span>HTTPS / SSE</span><i></i></div>
      <div class="route-end">
        <span class="route-icon dark"><Cpu :size="20" /></span>
        <div>
          <strong>{{
            gateway?.mode === "openai" ? "OpenAI" : "模型上游"
          }}</strong
          ><small>{{ gateway?.model || "正在读取配置" }}</small>
        </div>
      </div>
    </div>
    <div class="metrics-grid">
      <article>
        <span><Activity :size="16" />网关请求</span
        ><strong>{{ calls.length }}</strong
        ><small>最近 {{ calls.length }} 条已记录请求</small>
      </article>
      <article>
        <span><ArrowUpRight :size="16" />上游尝试</span
        ><strong>{{ attempts }}</strong
        ><small>被准入拒绝的请求不计入</small>
      </article>
      <article>
        <span><Clock3 :size="16" />平均耗时</span
        ><strong>{{ average }}<em>s</em></strong
        ><small>当前记录范围</small>
      </article>
      <article>
        <span><Cpu :size="16" />已报告 Token</span
        ><strong>{{ totalTokens.toLocaleString() }}</strong
        ><small>{{
          missing ? `${missing} 条用量未知` : "输入与输出合计"
        }}</small>
      </article>
    </div>
    <div class="gateway-columns">
      <section class="request-panel panel">
        <div class="panel-heading">
          <div>
            <h2>
              请求记录 <span class="count">{{ visible.length }}</span>
            </h2>
            <p>点击一条请求，查看它实际经过的处理步骤。</p>
          </div>
          <span class="live-badge"><i></i>实际请求</span>
        </div>
        <div class="request-filters">
          <div class="search-field">
            <Search :size="15" /><input
              v-model="filter"
              aria-label="筛选网关请求"
              placeholder="搜索 Trace ID、请求编号或模型"
            /><button
              v-if="filter"
              class="icon-button"
              aria-label="清除筛选"
              @click="filter = ''"
            >
              <X :size="13" />
            </button>
          </div>
          <select v-model="status" aria-label="请求结果">
            <option value="all">全部结果</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILED">失败</option>
            <option value="REJECTED">准入拒绝</option>
          </select>
        </div>
        <div class="request-table-wrap">
          <table>
            <thead>
              <tr>
                <th>请求 / 时间</th>
                <th>模型</th>
                <th>结果</th>
                <th>耗时</th>
                <th>Token</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="call in visible"
                :key="call.requestId"
                tabindex="0"
                :class="{ selected: selected?.requestId === call.requestId }"
                @click="selected = call"
                @keydown.enter="selected = call"
              >
                <td>
                  <code>{{ call.requestId.slice(0, 8) }}</code
                  ><small>{{ displayDate(call.createdAt) }}</small>
                </td>
                <td>
                  <strong>{{ call.model }}</strong
                  ><small>{{
                    call.kind === "embeddings" ? "向量化" : "流式对话"
                  }}</small>
                </td>
                <td>
                  <span class="pill" :class="call.status.toLowerCase()">{{
                    { SUCCESS: "成功", FAILED: "失败", REJECTED: "已拒绝" }[
                      call.status
                    ] || call.status
                  }}</span>
                </td>
                <td>{{ ms(call.durationMs) }}</td>
                <td>
                  {{
                    call.inputTokens === null
                      ? "未知"
                      : (
                          call.inputTokens + (call.outputTokens || 0)
                        ).toLocaleString()
                  }}
                </td>
                <td><ArrowUpRight :size="15" /></td>
              </tr>
            </tbody>
          </table>
          <div v-if="!visible.length" class="empty-state">
            <Network :size="32" />
            <h3>{{ filter ? "没有匹配的请求" : "等待第一条真实请求" }}</h3>
            <p>
              {{
                filter
                  ? "清除筛选后查看其他记录。"
                  : "在智能客服发送消息后，网关记录会出现在这里。"
              }}
            </p>
          </div>
        </div>
        <div class="panel-footer">
          <Database :size="13" /><span
            >{{
              data?.durable
                ? "记录已写入本地持久化日志"
                : "记录持久化状态待检查"
            }}
            · 当前展示最近 100 条</span
          >
        </div>
      </section>
      <aside class="policy-panel">
        <section class="panel">
          <div class="panel-heading">
            <h2><ShieldCheck :size="17" />生效策略</h2>
          </div>
          <dl class="policy-list">
            <div>
              <dt>窗口请求限额</dt>
              <dd>
                {{ data?.windowRequests ?? "—" }}
                <small>/ {{ data?.windowSeconds ?? 10 }} 秒</small>
              </dd>
            </div>
            <div>
              <dt>每日请求配额</dt>
              <dd>{{ data?.dailyRequests ?? "—" }}</dd>
            </div>
            <div>
              <dt>流式并发上限</dt>
              <dd>{{ data?.streamConcurrency ?? "—" }}</dd>
            </div>
            <div>
              <dt>流式自动重试</dt>
              <dd>{{ data?.streamRetry ? "已启用" : "未启用" }}</dd>
            </div>
            <div>
              <dt>流式熔断</dt>
              <dd>{{ data?.streamCircuitBreaker ? "已启用" : "未启用" }}</dd>
            </div>
          </dl>
          <p class="panel-note">
            展示当前真实流式入口的配置。修改服务器配置后重启生效。
          </p>
        </section>
        <section class="panel">
          <div class="panel-heading">
            <h2><Workflow :size="17" />用量事件</h2>
            <span
              class="pill"
              :class="gateway?.eventsEnabled ? 'success' : 'neutral'"
              >{{ gateway?.eventsEnabled ? "已启用" : "未启用" }}</span
            >
          </div>
          <dl class="policy-list">
            <div>
              <dt>Kafka 已确认</dt>
              <dd>
                {{
                  gateway?.eventsEnabled
                    ? (gateway.eventsAcknowledged ?? 0)
                    : "—"
                }}
              </dd>
            </div>
            <div>
              <dt>消费入库</dt>
              <dd>
                {{
                  gateway?.eventsEnabled ? (gateway.eventsPersisted ?? 0) : "—"
                }}
              </dd>
            </div>
            <div>
              <dt>消费者</dt>
              <dd>{{ gateway?.consumerState || "DISABLED" }}</dd>
            </div>
          </dl>
          <p class="panel-note">
            {{
              gateway?.eventsEnabled
                ? "以上为本次进程运行期间的计数。"
                : "当前启动模式未运行 Kafka 消费链路。"
            }}
          </p>
        </section>
      </aside>
    </div>
    <div v-if="selected" class="drawer-backdrop" @click.self="selected = null">
      <aside
        class="request-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="request-title"
        @keydown.esc="selected = null"
      >
        <div class="detail-top">
          <span>REQUEST DETAIL</span
          ><button
            class="icon-button"
            aria-label="关闭请求详情"
            @click="selected = null"
          >
            <X :size="20" />
          </button>
        </div>
        <h2 id="request-title">{{ selected.model }}</h2>
        <div class="request-meta">
          <span class="pill" :class="selected.status.toLowerCase()">{{
            selected.status
          }}</span
          ><span>{{ displayDate(selected.createdAt) }}</span>
        </div>
        <dl class="request-ids">
          <div>
            <dt>Request ID</dt>
            <dd>{{ selected.requestId }}</dd>
          </div>
          <div>
            <dt>Trace ID</dt>
            <dd>{{ selected.traceId }}</dd>
          </div>
          <div>
            <dt>调用应用</dt>
            <dd>{{ selected.appId }}</dd>
          </div>
        </dl>
        <div class="detail-metrics">
          <div>
            <span>总耗时</span><strong>{{ ms(selected.durationMs) }}</strong>
          </div>
          <div>
            <span>首次文本输出</span
            ><strong>{{ ms(selected.firstTokenMs) }}</strong>
          </div>
          <div>
            <span>上游尝试</span><strong>{{ selected.attempts }}</strong>
          </div>
        </div>
        <h3 class="section-title">实际执行步骤</h3>
        <div class="request-timeline">
          <article
            v-for="(step, index) in selected.steps"
            :key="index"
            :class="step.status.toLowerCase()"
          >
            <span class="step-marker"
              ><Check v-if="step.status === 'SUCCESS'" :size="14" /><CircleAlert
                v-else
                :size="14"
            /></span>
            <div>
              <div class="step-heading">
                <strong>{{ step.label }}</strong
                ><time>{{ ms(step.durationMs) }}</time>
              </div>
              <p>{{ step.summary }}</p>
              <div class="waterfall-track">
                <i
                  :style="{
                    marginLeft:
                      (step.offsetMs / Math.max(1, selected.durationMs)) * 80 +
                      '%',
                    width:
                      Math.max(
                        2,
                        (step.durationMs / Math.max(1, selected.durationMs)) *
                          80,
                      ) + '%',
                  }"
                ></i>
              </div>
            </div>
          </article>
        </div>
        <div class="usage-detail">
          <div>
            <ArrowDownLeft :size="18" /><span>输入 Token</span
            ><strong>{{ selected.inputTokens ?? "未知" }}</strong>
          </div>
          <div>
            <ArrowUpRight :size="18" /><span>输出 Token</span
            ><strong>{{ selected.outputTokens ?? "未知" }}</strong>
          </div>
        </div>
        <p class="muted">用量来自上游实际报告。记录仅保存运行元数据。</p>
      </aside>
    </div>
  </div>
</template>
