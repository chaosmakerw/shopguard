<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { RefreshCw, ArrowUpRight, Workflow, Search, X } from "@lucide/vue";
import { request, type Run, type Trace } from "../api";
import TraceView from "./TraceView.vue";
defineProps<{ admin: boolean }>();
const emit = defineEmits<{
  gateway: [traceId: string];
  order: [orderId: string];
}>();
const runs = ref<Run[]>([]),
  selected = ref<Run | null>(null),
  filter = ref(""),
  loading = ref(false),
  error = ref("");
const visible = computed(() =>
  runs.value.filter(
    (r) =>
      !filter.value ||
      r.id.includes(filter.value) ||
      r.code.includes(filter.value),
  ),
);
function trace(run: Run): Trace {
  try {
    return JSON.parse(run.details);
  } catch {
    return {};
  }
}
const date = (n: number) =>
  new Date(n).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
async function load() {
  loading.value = true;
  try {
    runs.value = await request("/runs");
    error.value = "";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "读取失败";
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
        <div class="overline">SHOPGUARD / TRACES</div>
        <h1>执行记录</h1>
        <p>
          {{
            admin ? "最近 100 次应用请求" : "你的最近 100 次请求"
          }}，从回答追溯到模型与工具。
        </p>
      </div>
      <button class="secondary" :disabled="loading" @click="load">
        <RefreshCw :size="15" :class="{ spin: loading }" />刷新
      </button>
    </header>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div>
    <section class="panel">
      <div class="panel-heading">
        <h2>
          业务请求 <span class="count">{{ runs.length }}</span>
        </h2>
        <div class="search-field">
          <Search :size="15" /><input
            v-model="filter"
            placeholder="搜索请求编号或结果"
            aria-label="筛选执行记录"
          />
        </div>
      </div>
      <div class="request-table-wrap">
        <table>
          <thead>
            <tr>
              <th>请求 / 时间</th>
              <th>任务</th>
              <th>结果</th>
              <th>模型 / 工具</th>
              <th>耗时</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="run in visible"
              :key="run.id"
              tabindex="0"
              @click="selected = run"
              @keydown.enter="selected = run"
            >
              <td>
                <code>{{ run.id.slice(0, 8) }}</code
                ><small>{{ date(run.createdAt) }}</small>
              </td>
              <td>
                {{ run.mode === "knowledge" ? "规则问答" : "交易助手"
                }}<small v-if="trace(run).orderId">关联订单</small>
              </td>
              <td>
                <span
                  class="pill"
                  :class="{
                    success: ['SUCCESS', 'ANSWERED', 'SUPPORT_HELP'].includes(run.code),
                    failed: ['FAILED', 'GENERATION_UNAVAILABLE', 'RETRIEVAL_UNAVAILABLE'].includes(run.code),
                  }"
                  >{{
                    {
                      SUCCESS: "完成",
                      ANSWERED: "已回答",
                      INSUFFICIENT_EVIDENCE: "资料不足",
                      OUT_OF_SCOPE: "超出服务范围",
                      SUPPORT_HELP: "客服指引",
                      ACTION_REQUIRED: "待用户确认",
                      ACCESS_BOUNDARY: "权限限制",
                      NEEDS_CLARIFICATION: "待补充信息",
                      UNVERIFIED_ANSWER: "未取得业务依据",
                    }[run.code] || "未完成"
                  }}</span
                >
              </td>
              <td>
                {{ trace(run).modelCalls || 0 }} 次调用<small>{{
                  trace(run).tools?.join(" · ") || "无工具调用"
                }}</small>
              </td>
              <td>{{ (run.durationMs / 1000).toFixed(2) }} s</td>
              <td><ArrowUpRight :size="15" /></td>
            </tr>
          </tbody>
        </table>
        <div v-if="!visible.length" class="empty-state">
          <Workflow :size="34" />
          <h3>这里会留下每一次执行的记录</h3>
          <p>完成一次对话后，回来看它如何得到答案。</p>
        </div>
      </div>
    </section>
    <div v-if="selected" class="drawer-backdrop" @click.self="selected = null">
      <aside
        class="request-drawer"
        role="dialog"
        aria-modal="true"
        aria-label="执行记录详情"
      >
        <div class="detail-top">
          <span>TRACE DETAIL</span
          ><button
            class="icon-button"
            aria-label="关闭执行详情"
            @click="selected = null"
          >
            <X :size="20" />
          </button>
        </div>
        <h2>{{ selected.mode === "knowledge" ? "规则问答" : "交易助手" }}</h2>
        <div class="mono">{{ selected.id }}</div>
        <p class="muted">
          {{ date(selected.createdAt) }} ·
          {{ (selected.durationMs / 1000).toFixed(2) }} s
        </p>
        <button
          v-if="trace(selected).orderId"
          class="linked-order"
          @click="emit('order', trace(selected).orderId!)"
        >
          查看关联订单 {{ trace(selected).orderId }}
          <ArrowUpRight :size="16" /></button
        ><TraceView
          :key="selected.id"
          :trace="trace(selected)"
          expanded
          @gateway="emit('gateway', $event)"
        />
      </aside>
    </div>
  </div>
</template>
