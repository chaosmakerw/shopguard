<script setup lang="ts">
import { computed, ref } from "vue";
import {
  Check,
  Clock3,
  CircleAlert,
  Cpu,
  Wrench,
  ChevronDown,
  ArrowUpRight,
  Layers3,
} from "@lucide/vue";
import type { Trace, GatewayCall } from "../api";
const props = defineProps<{
  trace: Trace;
  live?: boolean;
  expanded?: boolean;
}>();
const emit = defineEmits<{ gateway: [traceId: string] }>();
const open = ref(props.expanded || false);
const steps = computed(() => props.trace.steps || []);
const selected = ref<GatewayCall | null>(null);
const duration = computed(() =>
  Math.max(1, ...steps.value.map((s) => s.offsetMs + s.durationMs)),
);
const format = (ms: number) =>
  ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`;
</script>
<template>
  <section class="trace-view" :class="{ expanded: open || live }">
    <button
      class="trace-toggle"
      @click="open = !open"
      :aria-expanded="open || live"
    >
      <Layers3 :size="15" /><strong>{{
        live ? "正在执行" : "执行过程"
      }}</strong>
      <span
        >{{ trace.modelCalls || 0 }} 次模型调用<span v-if="trace.tools?.length">
          · {{ trace.tools.length }} 个工具</span
        ></span
      >
      <ChevronDown :size="15" :class="{ rotated: open || live }" />
    </button>
    <div v-if="open || live" class="trace-content">
      <div v-if="!steps.length" class="muted trace-empty">
        {{
          live
            ? "等待服务器返回执行步骤…"
            : "这条历史记录没有逐步追踪数据。新请求会记录实际执行过程。"
        }}
      </div>
      <div
        v-for="step in steps"
        :key="step.id"
        class="trace-step"
        :class="step.status.toLowerCase()"
      >
        <div class="step-marker">
          <Clock3
            v-if="step.status === 'RUNNING'"
            :size="14"
            class="spin"
          /><Check
            v-else-if="step.status === 'SUCCESS'"
            :size="14"
          /><CircleAlert v-else :size="14" />
        </div>
        <div class="step-body">
          <div class="step-heading">
            <component
              :is="step.kind === 'tool' ? Wrench : Cpu"
              :size="13"
            /><strong>{{ step.label }}</strong
            ><time>{{
              step.status === "RUNNING" ? "进行中" : format(step.durationMs)
            }}</time>
          </div>
          <p v-if="step.summary">{{ step.summary }}</p>
          <div class="waterfall-track">
            <i
              :style="{
                marginLeft: (step.offsetMs / duration) * 75 + '%',
                width: Math.max(2, (step.durationMs / duration) * 75) + '%',
              }"
            ></i>
          </div>
        </div>
      </div>
      <div v-if="trace.gatewayCalls?.length" class="gateway-chips">
        <button
          v-for="call in trace.gatewayCalls"
          :key="call.requestId"
          @click="
            selected = selected?.requestId === call.requestId ? null : call
          "
        >
          <Cpu :size="13" />{{ call.model
          }}<span>{{ format(call.durationMs) }}</span
          ><ArrowUpRight :size="13" />
        </button>
      </div>
      <div v-if="selected" class="gateway-inline">
        <div class="mono">{{ selected.requestId }}</div>
        <div v-for="(step, i) in selected.steps" :key="i" class="inline-step">
          <span
            :class="['dot', step.status === 'SUCCESS' ? 'green' : 'red']"
          ></span
          ><strong>{{ step.label }}</strong
          ><span>{{ step.durationMs }} ms</span>
          <p>{{ step.summary }}</p>
        </div>
        <button class="text-button" @click="emit('gateway', selected.traceId)">
          查看 Gateway 关联请求 <ArrowUpRight :size="13" />
        </button>
      </div>
      <div class="trace-footer">
        <code v-if="trace.traceId">{{ trace.traceId.slice(0, 8) }}</code
        ><span v-if="trace.modelCalls === 0 && !steps.some(s => s.kind === 'embedding')">本次未调用模型 API</span
        ><span v-else
          >输入 {{ trace.inputTokens ?? "未知" }} / 输出
          {{ trace.outputTokens ?? "未知" }} Token</span
        ><span v-if="!trace.usageComplete && (trace.modelCalls || steps.some(s => s.kind === 'embedding'))" class="muted">用量未完整报告</span>
      </div>
    </div>
  </section>
</template>
