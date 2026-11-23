<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  Database,
  Table2,
  RefreshCw,
  ChevronRight,
  ChevronLeft,
  LockKeyhole,
  Check,
  ArrowRight,
  Copy,
} from "@lucide/vue";
import { request } from "../api";
import { errorText } from "../commerce";
type DbTable = { name: string; label: string; purpose: string; rows: number };
type DatabaseInfo = {
  engine: string;
  version: string;
  persistent: boolean;
  connected: boolean;
  tables: DbTable[];
};
type Rows = {
  name: string;
  columns: string[];
  items: Record<string, unknown>[];
  total: number;
  page: number;
  size: number;
  query: string;
};
const data = ref<DatabaseInfo>(),
  active = ref("orders"),
  rows = ref<Rows>(),
  page = ref(1),
  loading = ref(false),
  error = ref(""),
  copied = ref(false);
let loadVersion = 0;
async function select(name: string) {
  active.value = name;
  page.value = 1;
  await loadRows();
}
async function loadRows() {
  const version = ++loadVersion;
  loading.value = true;
  error.value = "";
  rows.value = undefined;
  copied.value = false;
  try {
    const result = await request<Rows>(
      "/admin/commerce/database/" +
        active.value +
        "?page=" +
        page.value +
        "&size=12",
    );
    if (version === loadVersion) rows.value = result;
  } catch (e) {
    if (version === loadVersion) error.value = errorText(e);
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}
async function load() {
  try {
    data.value = await request("/admin/commerce/database");
    await loadRows();
  } catch (e) {
    error.value = errorText(e);
  }
}
async function copy() {
  if (!rows.value) return;
  try {
    await navigator.clipboard.writeText(rows.value.query);
    copied.value = true;
    setTimeout(() => (copied.value = false), 1800);
  } catch {
    error.value = "复制失败，可直接选择下方查询语句复制。";
  }
}
onMounted(load);
</script>
<template>
  <div class="content-page database-page">
    <header class="page-heading">
      <div>
        <h1>数据库</h1>
      </div>
      <button class="secondary" :disabled="loading" @click="load">
        <RefreshCw :size="15" />刷新数据
      </button>
    </header>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div>
    <template v-if="data"
      ><div class="database-banner">
        <span class="database-symbol"><Database :size="28" /></span>
        <div>
          <h2>
            {{ data.engine
            }}<span class="status-label paid"
              ><span class="live-dot"></span>已连接</span
            >
          </h2>
          <p>
            {{ data.persistent ? "持久化数据库" : "内存测试数据库" }} ·
            {{ data.version }}
          </p>
        </div>
        <div class="database-count">
          <strong>{{ data.tables.length }}</strong
          ><span>业务数据表</span>
        </div>
        <div class="database-count">
          <strong>{{ data.tables.reduce((sum, t) => sum + t.rows, 0) }}</strong
          ><span>可查看记录</span>
        </div>
      </div>
      <section class="surface-panel data-flow">
        <header>
          <div>
            <h2>业务数据关系</h2>
            <p>点击节点，查看对应数据表。</p>
          </div>
          <LockKeyhole :size="18" />
        </header>
        <div class="data-flow-nodes">
          <template
            v-for="(name, index) in [
              'products',
              'trade_inventory',
              'orders',
              'commerce_returns',
              'commerce_refunds',
            ]"
            :key="name"
            ><ArrowRight v-if="index" :size="18" /><button
              :class="{ active: active === name }"
              @click="select(name)"
            >
              <Table2 :size="18" /><strong>{{
                data.tables.find((t) => t.name === name)?.label
              }}</strong
              ><span
                >{{ data.tables.find((t) => t.name === name)?.rows }} 条</span
              >
            </button></template
          >
        </div>
      </section>
      <div class="database-layout">
        <aside class="surface-panel database-tables">
          <header>
            <h3>数据表</h3>
            <span>{{ data.tables.length }}</span>
          </header>
          <button
            v-for="table in data.tables"
            :key="table.name"
            :class="{ active: active === table.name }"
            @click="select(table.name)"
          >
            <Table2 :size="16" /><span
              ><strong>{{ table.label }}</strong
              ><small>{{ table.name }}</small></span
            ><b>{{ table.rows }}</b>
          </button>
        </aside>
        <section class="surface-panel data-viewer">
          <header>
            <div>
              <h2>{{ data.tables.find((t) => t.name === active)?.label }}</h2>
              <p>{{ data.tables.find((t) => t.name === active)?.purpose }}</p>
            </div>
            <span class="read-only-badge"><LockKeyhole :size="13" />只读</span>
          </header>
          <div v-if="loading" class="empty-panel" role="status">
            <RefreshCw class="spin" :size="24" />正在读取记录…
          </div>
          <div v-else-if="rows" class="table-scroll">
            <table class="business-table raw-data-table">
              <thead>
                <tr>
                  <th v-for="column in rows.columns" :key="column">
                    {{ column }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in rows.items" :key="index">
                  <td
                    v-for="column in rows.columns"
                    :key="column"
                    :class="{ mono: column.includes('id') }"
                  >
                    {{
                      row[column] === null
                        ? "NULL"
                        : typeof row[column] === "boolean"
                          ? row[column]
                            ? "true"
                            : "false"
                          : row[column]
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!rows.items.length" class="empty-panel">
              <Database :size="30" />
              <h3>这张表暂时没有记录</h3>
              <p>完成对应业务操作后，刷新即可查看。</p>
            </div>
          </div>
          <footer v-if="rows" class="table-pagination">
            <span>共 {{ rows.total }} 条记录</span>
            <div>
              <button
                class="icon-button"
                aria-label="数据上一页"
                :disabled="page === 1 || loading"
                @click="
                  page--;
                  loadRows();
                "
              >
                <ChevronLeft :size="16" /></button
              ><span>{{ page }}</span
              ><button
                class="icon-button"
                aria-label="数据下一页"
                :disabled="page * 12 >= rows.total || loading"
                @click="
                  page++;
                  loadRows();
                "
              >
                <ChevronRight :size="16" />
              </button>
            </div>
          </footer>
          <div v-if="rows" class="query-preview">
            <div>
              <span>当前查询</span
              ><button @click="copy">
                <Check v-if="copied" :size="13" /><Copy v-else :size="13" />{{
                  copied ? "已复制" : "复制 SQL"
                }}
              </button>
            </div>
            <code>{{ rows.query }}</code>
          </div>
        </section>
      </div>
      <p class="database-footnote">
        <LockKeyhole :size="14" />数据查看仅对管理员开放。账户密码、API
        密钥和对话正文不在此处展示。
      </p></template
    >
  </div>
</template>
