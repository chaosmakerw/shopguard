<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from "vue";
import {
  ArrowUp,
  ArrowUpRight,
  ArrowRight,
  BookOpen,
  LayoutDashboard,
  Boxes,
  ReceiptText,
  RotateCcw,
  Database,
  CircleHelp,
  Cpu,
  FileText,
  Layers3,
  LogOut,
  MessageSquare,
  Network,
  Plus,
  Search,
  Settings2,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Square,
  Trash2,
  Upload,
  Workflow,
  X,
  Menu,
  RefreshCw,
} from "@lucide/vue";
import CitationSource from "./CitationSource.vue";
import MarkdownText from "./components/MarkdownText.vue";
import TraceView from "./components/TraceView.vue";
import TradeDesk from "./components/TradeDesk.vue";
import GatewayDesk from "./components/GatewayDesk.vue";
import RunDesk from "./components/RunDesk.vue";
import OverviewDesk from "./components/OverviewDesk.vue";
import CatalogDesk from "./components/CatalogDesk.vue";
import OperationsDesk from "./components/OperationsDesk.vue";
import AfterSalesDesk from "./components/AfterSalesDesk.vue";
import DatabaseDesk from "./components/DatabaseDesk.vue";
import { api as tradeApi } from "../../trade-web/src/api";
import {
  currentSession,
  post,
  request,
  streamChat,
  type Identity,
  type Session,
  type Message,
  type Doc,
  type Settings,
  type Citation,
  type Trace,
  type TraceStep,
  type GatewayCall,
} from "./api";
import type { TradeOrder } from "../../trade-web/src/types";

type Page =
  | "chat"
  | "trade"
  | "knowledge"
  | "runs"
  | "gateway"
  | "settings"
  | "overview"
  | "catalog"
  | "operations"
  | "aftersales"
  | "database";
const adminPages: Page[] = [
  "overview",
  "catalog",
  "operations",
  "database",
  "gateway",
];
const pages: Page[] = [
  "chat",
  "trade",
  "knowledge",
  "runs",
  "gateway",
  "settings",
  "overview",
  "catalog",
  "operations",
  "aftersales",
  "database",
];
const hashPage = () => {
  const value = location.hash.replace("#/", "") as Page;
  return pages.includes(value) ? value : "overview";
};
const user = ref<Identity | null>(null),
  ready = ref(false),
  error = ref(""),
  notice = ref(""),
  page = ref<Page>(hashPage()),
  mobileNav = ref(false);
const username = ref(""),
  password = ref(""),
  loggingIn = ref(false),
  sessions = ref<Session[]>([]),
  selected = ref(""),
  mode = ref<"business" | "knowledge">("business");
const messages = ref<Message[]>([]),
  question = ref(""),
  sending = ref(false),
  phase = ref(""),
  draft = ref(""),
  transcript = ref<HTMLElement>(),
  switching = ref(false);
const settings = ref<Settings | null>(null),
  health = ref<Record<string, unknown> | null>(null),
  docs = ref<Doc[]>([]),
  loading = ref(false);
const showImport = ref(false),
  docTitle = ref(""),
  docContent = ref(""),
  importing = ref(false),
  contextOrder = ref<TradeOrder | null>(null),
  focusOrder = ref(""),
  gatewayTrace = ref("");
const pendingDelete = ref<{
  kind: "session" | "document";
  id: string;
  title: string;
} | null>(null);
const traceModal = ref(false);
const afterSalesOrder = ref("");
const liveTrace = ref<Trace>({ steps: [], gatewayCalls: [] }),
  activeTrace = ref<Trace | null>(null);
let activeRequest: AbortController | null = null;
const admin = computed(() => user.value?.role === "ADMIN");
const activeSession = computed(() =>
  sessions.value.find((s) => s.id === selected.value),
);
const title = computed(
  () =>
    ({
      chat: "智能客服",
      trade: "商品与订单",
      knowledge: "知识库",
      runs: "执行记录",
      gateway: "模型网关",
      settings: "连接与配置",
      overview: "经营概览",
      catalog: "商品与库存",
      operations: "订单管理",
      aftersales: "售后中心",
      database: "数据库",
    })[page.value],
);
const examples = computed(() =>
  mode.value === "business"
    ? [
        {
          icon: Search,
          title: "发现合适的商品",
          text: "找几本 Java 相关的书，告诉我价格",
        },
        {
          icon: ShoppingBag,
          title: "查询我的订单",
          text: "查询我的订单，说明当前状态和下一步可以做什么",
        },
        {
          icon: BookOpen,
          title: "了解商品详情",
          text: "商品 9001 的详细信息是什么？",
        },
      ]
    : [
        {
          icon: CircleHelp,
          title: "取消规则",
          text: "未付款的订单可以取消吗？",
        },
        {
          icon: ShieldCheck,
          title: "支付与交易",
          text: "已经支付的订单怎么处理？",
        },
        {
          icon: FileText,
          title: "资料与依据",
          text: "付款演示会扣除真实资金吗？",
        },
      ],
);
const date = (n: number) =>
  new Date(n).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
function showError(reason: unknown) {
  error.value =
    reason instanceof Error ? reason.message : "操作未完成，请稍后重试";
}
async function scrollBottom() {
  await nextTick();
  transcript.value?.scrollTo({
    top: transcript.value.scrollHeight,
    behavior: "smooth",
  });
}
async function loadPage(target: Page) {
  loading.value = true;
  try {
    if (target === "knowledge") docs.value = await request("/knowledge");
    if (target === "settings" && admin.value)
      health.value = await request("/admin/health");
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
async function initialize() {
  const result = await currentSession();
  user.value = result.user;
  if (!user.value) return;
  [sessions.value, settings.value] = await Promise.all([
    request<Session[]>("/sessions"),
    request<Settings>("/settings"),
  ]);
  if (adminPages.includes(page.value) && !admin.value) page.value = "trade";
  if (page.value === "chat" && sessions.value[0])
    await selectSession(sessions.value[0]);
  await loadPage(page.value);
}
async function login() {
  loggingIn.value = true;
  error.value = "";
  try {
    await currentSession();
    await post("/login", {
      username: username.value,
      password: password.value,
    });
    password.value = "";
    await initialize();
  } catch (e) {
    showError(e);
  } finally {
    loggingIn.value = false;
  }
}
async function logout() {
  activeRequest?.abort();
  try {
    await post("/logout");
    user.value = null;
    sessions.value = [];
    messages.value = [];
    selected.value = "";
    contextOrder.value = null;
    activeTrace.value = null;
    focusOrder.value = "";
    gatewayTrace.value = "";
    await currentSession();
  } catch (e) {
    showError(e);
  }
}
async function navigate(target: Page) {
  if (sending.value || switching.value) {
    notice.value = "请等待当前回答完成，或先停止接收。";
    return;
  }
  if (adminPages.includes(target) && !admin.value) {
    notice.value = "此页面需要管理员身份。";
    return;
  }
  page.value = target;
  location.hash = "/" + target;
  mobileNav.value = false;
  error.value = "";
  notice.value = "";
  await loadPage(target);
}
async function selectSession(session: Session) {
  if (sending.value || switching.value) return;
  switching.value = true;
  selected.value = session.id;
  mode.value = session.mode;
  page.value = "chat";
  location.hash = "/chat";
  draft.value = "";
  contextOrder.value = null;
  activeTrace.value = null;
  mobileNav.value = false;
  error.value = "";
  try {
    const result = await request<{ messages: Message[]; details: string[] }>(
      "/sessions/" + encodeURIComponent(session.id),
    );
    const annotations = new Map<number, Trace>();
    for (const raw of result.details) {
      try {
        const entry = JSON.parse(raw) as Trace;
        if (entry.sequence) annotations.set(entry.sequence, entry);
      } catch {
        /* Historical optional metadata. */
      }
    }
    messages.value = result.messages.map((message) => {
      const trace = annotations.get(message.sequence || 0);
      return {
        ...message,
        trace,
        citations: trace?.citations,
        orderId: trace?.orderId,
      };
    });
    activeTrace.value =
      [...messages.value].reverse().find((m) => m.trace)?.trace || null;
    await scrollBottom();
  } catch (e) {
    showError(e);
  } finally {
    switching.value = false;
  }
}
async function newSession(selectedMode = mode.value) {
  if (sending.value || switching.value) return;
  switching.value = true;
  let created: Session | undefined;
  try {
    const result = await post<{ id: string }>("/sessions", {
      mode: selectedMode,
    });
    sessions.value = await request("/sessions");
    created = sessions.value.find((s) => s.id === result.id);
  } catch (e) {
    showError(e);
  } finally {
    switching.value = false;
  }
  if (created) await selectSession(created);
  return created;
}
async function send(text = question.value) {
  text = text.trim();
  if (!text || sending.value || switching.value) return;
  if (!selected.value) await newSession();
  if (!selected.value) return;
  question.value = "";
  error.value = "";
  notice.value = "";
  sending.value = true;
  phase.value = "正在连接模型";
  draft.value = "";
  liveTrace.value = { steps: [], gatewayCalls: [], modelCalls: 0, tools: [] };
  activeRequest = new AbortController();
  messages.value.push({ role: "user", content: text });
  await scrollBottom();
  try {
    await streamChat(
      selected.value,
      text,
      activeRequest.signal,
      (type, data) => {
        if (type === "trace")
          liveTrace.value.traceId = (data as { traceId: string }).traceId;
        if (type === "phase") {
          phase.value = (data as { label: string }).label;
          draft.value = "";
        }
        if (type === "tools") {
          const names = data as string[];
          phase.value = names.includes("list_my_orders")
            ? "正在查询你的订单"
            : "正在执行工具";
          liveTrace.value.tools = [...(liveTrace.value.tools || []), ...names];
          draft.value = "";
        }
        if (type === "step") {
          const step = data as TraceStep;
          const current = liveTrace.value.steps || [];
          const index = current.findIndex((s) => s.id === step.id);
          if (index < 0) current.push(step);
          else current[index] = step;
          liveTrace.value.steps = current;
          liveTrace.value.modelCalls = current.filter(
            (s) => s.kind === "model",
          ).length;
        }
        if (type === "gateway") {
          const call = data as GatewayCall;
          liveTrace.value.gatewayCalls = [
            ...(liveTrace.value.gatewayCalls || []),
            call,
          ];
        }
        if (type === "delta") {
          draft.value += String(data);
          void scrollBottom();
        }
        if (type === "done") {
          const result = data as {
            answer: string;
            citations: Citation[];
            trace: Trace;
            orderId?: string;
          };
          messages.value.push({
            role: "assistant",
            content: result.answer,
            citations: result.citations,
            trace: result.trace,
            orderId: result.orderId,
          });
          activeTrace.value = result.trace;
          draft.value = "";
        }
      },
      contextOrder.value?.orderId,
    );
    sessions.value = await request("/sessions");
    await scrollBottom();
  } catch (e) {
    activeTrace.value = { ...liveTrace.value };
    if (activeRequest.signal.aborted)
      notice.value = "已停止接收。重新打开会话可查看服务器已保存的结果。";
    else showError(e);
  } finally {
    sending.value = false;
    activeRequest = null;
    phase.value = "";
  }
}
function questionKey(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    void send();
  }
}
async function askOrder(order: TradeOrder) {
  const created = await newSession("business");
  if (!created) return;
  contextOrder.value = order;
  await send("请查询这笔订单的当前状态，告诉我接下来可以做什么。");
}
function inspectTrace(trace: Trace) {
  activeTrace.value = trace;
  if (window.innerWidth <= 1000) traceModal.value = true;
}
async function openOrder(id: string) {
  focusOrder.value = id;
  await navigate("trade");
}
async function openAfterSales(id = "") {
  afterSalesOrder.value = id;
  await navigate("aftersales");
}
async function askAfterSales(id: string) {
  try {
    await askOrder(await tradeApi.order(id));
  } catch (e) {
    showError(e);
  }
}
async function openGateway(id = "") {
  traceModal.value = false;
  gatewayTrace.value = id;
  await navigate("gateway");
}
async function removeConfirmed() {
  const pending = pendingDelete.value;
  if (!pending) return;
  try {
    await request(
      pending.kind === "session"
        ? "/sessions/" + encodeURIComponent(pending.id)
        : "/admin/knowledge/" + encodeURIComponent(pending.id),
      { method: "DELETE" },
    );
    if (pending.kind === "session") {
      sessions.value = await request("/sessions");
      if (selected.value === pending.id) {
        selected.value = "";
        messages.value = [];
        activeTrace.value = null;
        contextOrder.value = null;
      }
    } else docs.value = await request("/knowledge");
    pendingDelete.value = null;
  } catch (e) {
    showError(e);
  }
}
async function importDocument() {
  importing.value = true;
  error.value = "";
  try {
    await post("/admin/knowledge", {
      title: docTitle.value,
      content: docContent.value,
    });
    docs.value = await request("/knowledge");
    showImport.value = false;
    docTitle.value = "";
    docContent.value = "";
    notice.value = "文档已完成向量化，可以开始规则问答。";
  } catch (e) {
    showError(e);
  } finally {
    importing.value = false;
  }
}
async function importSample() {
  importing.value = true;
  error.value = "";
  try {
    await post("/admin/knowledge/sample");
    docs.value = await request("/knowledge");
    notice.value = "示例规则已入库。";
  } catch (e) {
    showError(e);
  } finally {
    importing.value = false;
  }
}
async function loadFile(event: Event) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  if (!file) return;
  if (file.size > 262144 || !/\.(md|txt)$/i.test(file.name)) {
    error.value = "请选择 256KB 以内的 UTF-8 Markdown 或文本文件";
    return;
  }
  try {
    docContent.value = new TextDecoder("utf-8", { fatal: true }).decode(
      await file.arrayBuffer(),
    );
    docTitle.value = file.name;
    showImport.value = true;
  } catch {
    error.value = "文件需要使用 UTF-8 编码";
  } finally {
    input.value = "";
  }
}
function unauthorized() {
  user.value = null;
  activeRequest?.abort();
}
function hashChanged() {
  const target = hashPage();
  if (target !== page.value) void navigate(target);
}
onMounted(async () => {
  window.addEventListener("shopguard:unauthorized", unauthorized);
  window.addEventListener("hashchange", hashChanged);
  try {
    await initialize();
  } catch (e) {
    showError(e);
  } finally {
    ready.value = true;
  }
});
onUnmounted(() => {
  activeRequest?.abort();
  window.removeEventListener("shopguard:unauthorized", unauthorized);
  window.removeEventListener("hashchange", hashChanged);
});
</script>

<template>
  <div v-if="!ready" class="boot">
    <span class="brand-symbol"><Layers3 :size="24" /></span>
    <p>正在连接工作台…</p>
  </div>
  <div v-else-if="!user" class="login-page">
    <section class="login-story">
      <a class="brand"
        ><span class="brand-symbol"><Layers3 :size="23" /></span>ShopGuard<span
          class="brand-version"
          >WORKSPACE</span
        ></a
      >
      <div class="login-story-copy">
        <span class="overline">COMMERCE, WITH CONTEXT.</span>
        <h1>每一笔交易，<br />都有清晰的来路。</h1>
        <p>
          管理商品与订单，让 AI 查询真实业务，<br />也看见每一次回答的执行过程。
        </p>
        <div class="login-path">
          <span><ShoppingBag :size="19" />业务订单</span><i></i
          ><span><ShieldCheck :size="19" />模型网关</span><i></i
          ><span><Sparkles :size="19" />AI 助手</span>
        </div>
      </div>
      <footer>
        <span>ShopGuard + LLM Gateway</span><span>PRIVATE WORKSPACE</span>
      </footer>
    </section>
    <section class="login-side">
      <form @submit.prevent="login" class="login-form">
        <span class="login-mark"><Layers3 :size="30" /></span>
        <h2>进入工作台</h2>
        <p>登录后继续你的交易与 AI 工作。</p>
        <label for="username">账户</label
        ><input
          id="username"
          v-model="username"
          autocomplete="username"
          required
          placeholder="输入账户名称"
        /><label for="password">密码</label
        ><input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
          placeholder="输入密码"
        />
        <div v-if="error" role="alert" class="login-error">{{ error }}</div>
        <button class="primary wide" :disabled="loggingIn">
          {{ loggingIn ? "正在登录…" : "登录工作台" }}<ArrowRight :size="17" />
        </button>
        <p class="login-note"><ShieldCheck :size="14" />密钥由服务器管理</p>
      </form>
    </section>
  </div>
  <div v-else class="app-shell" :class="{ 'nav-open': mobileNav }">
    <div v-if="mobileNav" class="nav-scrim" @click="mobileNav = false"></div>
    <aside class="sidebar">
      <a
        class="brand"
        :href="admin ? '#/overview' : '#/trade'"
        @click.prevent="navigate(admin ? 'overview' : 'trade')"
        ><span class="brand-symbol"><Layers3 :size="22" /></span
        ><span>ShopGuard<small>交易与智能服务</small></span></a
      >
      <div class="workspace-switch">
        <span class="workspace-avatar">S</span>
        <div>
          <strong>校园交易工作台</strong
          ><small>{{ admin ? "运营管理空间" : "我的交易空间" }}</small>
        </div>
      </div>
      <div class="nav-label">工作空间</div>
      <nav aria-label="主导航">
        <button
          v-if="admin"
          :class="{ active: page === 'overview' }"
          @click="navigate('overview')"
        >
          <LayoutDashboard :size="18" />经营概览
        </button>
        <button
          v-if="admin"
          :class="{ active: page === 'catalog' }"
          @click="navigate('catalog')"
        >
          <Boxes :size="18" />商品与库存
        </button>
        <button
          v-if="admin"
          :class="{ active: page === 'operations' }"
          @click="navigate('operations')"
        >
          <ReceiptText :size="18" />订单管理
        </button>
        <button
          :class="{ active: page === 'aftersales' }"
          @click="openAfterSales()"
        >
          <RotateCcw :size="18" />售后中心
        </button>
        <div class="nav-label">交易与 AI</div>
        <button
          :class="{ active: page === 'trade' }"
          @click="navigate('trade')"
        >
          <ShoppingBag :size="18" />商品与订单</button
        ><button :class="{ active: page === 'chat' }" @click="navigate('chat')">
          <Sparkles :size="18" />智能客服<span class="nav-hint"
            >AI</span
          ></button
        ><button
          :class="{ active: page === 'knowledge' }"
          @click="navigate('knowledge')"
        >
          <BookOpen :size="18" />知识库</button
        ><button :class="{ active: page === 'runs' }" @click="navigate('runs')">
          <Workflow :size="18" />执行记录
        </button>
        <div v-if="admin" class="nav-label">运行管理</div>
        <button
          v-if="admin"
          :class="{ active: page === 'database' }"
          @click="navigate('database')"
        >
          <Database :size="18" />数据库
        </button>
        <button
          v-if="admin"
          :class="{ active: page === 'gateway' }"
          @click="
            gatewayTrace = '';
            navigate('gateway');
          "
        >
          <Network :size="18" />模型网关<ArrowUpRight
            :size="14"
            class="nav-arrow"
          /></button
        ><button
          :class="{ active: page === 'settings' }"
          @click="navigate('settings')"
        >
          <Settings2 :size="18" />连接与配置
        </button>
      </nav>
      <div class="sessions-heading">
        <span class="nav-label">最近会话</span
        ><button
          class="icon-button"
          aria-label="新建会话"
          :disabled="sending || switching"
          @click="newSession()"
        >
          <Plus :size="15" />
        </button>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-row"
          :class="{ active: page === 'chat' && selected === session.id }"
        >
          <button
            :disabled="sending || switching"
            @click="selectSession(session)"
          >
            <MessageSquare :size="14" /><span>{{ session.title }}</span></button
          ><button
            class="session-delete"
            :disabled="sending || switching"
            :aria-label="'删除会话 ' + session.title"
            @click="
              pendingDelete = {
                kind: 'session',
                id: session.id,
                title: session.title,
              }
            "
          >
            <X :size="13" />
          </button>
        </div>
        <p v-if="!sessions.length" class="no-sessions">你的会话会保存在这里</p>
      </div>
      <div class="sidebar-bottom">
        <div class="user-avatar">{{ user.displayName.slice(0, 1) }}</div>
        <div>
          <strong>{{ user.displayName }}</strong
          ><small>{{ admin ? "管理员" : "用户" }}</small>
        </div>
        <button class="icon-button" aria-label="退出登录" @click="logout">
          <LogOut :size="16" />
        </button>
      </div>
    </aside>
    <main class="main-panel">
      <header class="topbar">
        <button
          class="icon-button mobile-menu"
          aria-label="打开导航"
          @click="mobileNav = !mobileNav"
        >
          <Menu :size="20" />
        </button>
        <div class="breadcrumb">
          <span>工作空间</span><span>/</span><strong>{{ title }}</strong>
        </div>
        <div class="topbar-right">
          <span class="mode-badge"
            ><Cpu :size="14" />{{
              settings?.mode === "openai"
                ? "OpenAI · 真实 API 模式"
                : "本地替身模式"
            }}</span
          ><button
            class="icon-button"
            aria-label="新建对话"
            :disabled="sending || switching"
            @click="newSession('business')"
          >
            <Plus :size="18" />
          </button>
        </div>
      </header>
      <div v-if="error" class="global-alert alert error" role="alert">
        {{ error
        }}<button aria-label="关闭错误" @click="error = ''">
          <X :size="16" />
        </button>
      </div>
      <div v-if="notice" class="global-alert alert success" role="status">
        {{ notice
        }}<button aria-label="关闭提示" @click="notice = ''">
          <X :size="16" />
        </button>
      </div>
      <OverviewDesk v-if="page === 'overview' && admin" @navigate="navigate" />
      <CatalogDesk v-else-if="page === 'catalog' && admin" />
      <OperationsDesk
        v-else-if="page === 'operations' && admin"
        @returns="openAfterSales()"
      />
      <DatabaseDesk v-else-if="page === 'database' && admin" />
      <AfterSalesDesk
        v-else-if="page === 'aftersales'"
        :admin="admin"
        :order-id="afterSalesOrder"
        @order="openOrder"
        @ask="askAfterSales"
        @consumed="afterSalesOrder = ''"
      />
      <TradeDesk
        v-else-if="page === 'trade'"
        :focus-order="focusOrder"
        @ask="askOrder"
        @aftersales="openAfterSales"
      />
      <GatewayDesk
        v-else-if="page === 'gateway' && admin"
        :trace-id="gatewayTrace"
      />
      <RunDesk
        v-else-if="page === 'runs'"
        :admin="admin"
        @gateway="openGateway"
        @order="openOrder"
      />
      <section v-else-if="page === 'chat'" class="chat-page">
        <header class="chat-heading">
          <div>
            <h1>
              {{
                activeSession?.title === "新对话" || !activeSession
                  ? "智能客服"
                  : activeSession.title
              }}
            </h1>
            <p>
              {{
                mode === "business"
                  ? "查询平台商品、本人订单与售后；也可以咨询交易规则。"
                  : "仅解答平台交易规则，回答附可核对的原文引用。"
              }}
            </p>
          </div>
          <div class="segmented">
            <button
              :class="{ active: mode === 'business' }"
              :disabled="sending || switching"
              @click="newSession('business')"
            >
              交易助手</button
            ><button
              :class="{ active: mode === 'knowledge' }"
              :disabled="sending || switching"
              @click="newSession('knowledge')"
            >
              规则问答
            </button>
          </div>
        </header>
        <div
          class="conversation-layout"
          :class="{ 'has-trace': sending || activeTrace }"
        >
          <div class="conversation-main">
            <div ref="transcript" class="transcript">
              <div v-if="!messages.length && !sending" class="chat-welcome">
                <span class="welcome-symbol"
                  ><Sparkles :size="30" :stroke-width="1.5" /></span
                ><span class="overline">YOUR COMMERCE COPILOT</span>
                <h2>
                  {{
                    mode === "business"
                      ? "从一个具体问题开始。"
                      : "让每个答案，都有依据。"
                  }}
                </h2>
                <p>
                  {{
                    mode === "business"
                      ? "查商品、订单和售后进度。平台以外的问题不在客服服务范围内。"
                      : "查询平台规则，展开引用即可核对原文。"
                  }}
                </p>
                <div class="prompt-grid">
                  <button
                    v-for="example in examples"
                    :key="example.title"
                    :disabled="sending || switching"
                    @click="send(example.text)"
                  >
                    <component :is="example.icon" :size="20" /><strong>{{
                      example.title
                    }}</strong
                    ><span>{{ example.text }}</span
                    ><ArrowUpRight :size="16" />
                  </button>
                </div>
                <div class="welcome-route">
                  <span><ShoppingBag :size="14" />业务数据</span><i></i
                  ><span><ShieldCheck :size="14" />LLM Gateway</span><i></i
                  ><span
                    ><Cpu :size="14" />{{
                      settings?.mode === "openai" ? "OpenAI" : "本地替身"
                    }}</span
                  >
                </div>
              </div>
              <article
                v-for="(message, index) in messages"
                :key="index"
                class="message"
                :class="message.role"
              >
                <div class="message-avatar">
                  <Sparkles
                    v-if="message.role === 'assistant'"
                    :size="17"
                  /><span v-else>{{ user.displayName.slice(0, 1) }}</span>
                </div>
                <div class="message-body">
                  <div class="message-name">
                    {{ message.role === "assistant" ? "ShopGuard" : "你"
                    }}<span
                      v-if="message.role === 'assistant'"
                      class="message-origin"
                      >AI 助手</span
                    >
                  </div>
                  <MarkdownText :text="message.content" /><button
                    v-if="message.orderId"
                    class="linked-order"
                    @click="openOrder(message.orderId)"
                  >
                    <ShoppingBag :size="17" /><span
                      >查看关联订单<small>{{ message.orderId }}</small></span
                    ><ArrowUpRight :size="15" /></button
                  ><button
                    v-else-if="message.trace?.tools?.includes('list_my_orders')"
                    class="text-button"
                    @click="navigate('trade')"
                  >
                    打开商品与订单 <ArrowUpRight :size="14" />
                  </button>
                  <div v-if="message.citations?.length" class="citations">
                    <CitationSource
                      v-for="(citation, c) in message.citations"
                      :key="c"
                      :citation="citation"
                      :index="c"
                    />
                  </div>
                  <button
                    v-if="message.trace"
                    class="trace-link"
                    @click="inspectTrace(message.trace)"
                  >
                    <Workflow :size="14" />{{
                      message.trace.modelCalls || 0
                    }}
                    次模型调用 · 查看执行过程<ArrowUpRight :size="13" />
                  </button>
                </div>
              </article>
              <article v-if="sending" class="message assistant">
                <div class="message-avatar"><Sparkles :size="17" /></div>
                <div class="message-body">
                  <div class="message-name">
                    ShopGuard<span class="phase">{{ phase }}</span>
                  </div>
                  <MarkdownText v-if="draft" :text="draft" />
                  <div v-else class="typing"><i></i><i></i><i></i></div>
                </div>
              </article>
            </div>
            <div class="composer-area">
              <button
                v-if="sending"
                class="mobile-trace text-button"
                @click="traceModal = true"
              >
                <Workflow :size="14" />查看执行过程
              </button>
              <div v-if="contextOrder" class="context-order">
                <ShoppingBag :size="15" /><span>{{
                  contextOrder.productName
                }}</span
                ><code>{{ contextOrder.orderId }}</code
                ><button
                  class="icon-button"
                  :disabled="sending"
                  aria-label="移除订单上下文"
                  @click="contextOrder = null"
                >
                  <X :size="13" />
                </button>
              </div>
              <form class="composer" @submit.prevent="send()">
                <textarea
                  v-model="question"
                  :disabled="sending || switching"
                  :placeholder="
                    mode === 'knowledge'
                      ? '想了解哪条平台规则？'
                      : '查询平台商品、本人订单或售后进度…'
                  "
                  maxlength="4000"
                  rows="2"
                  aria-label="输入问题"
                  @keydown="questionKey"
                ></textarea>
                <div class="composer-actions">
                  <span
                    ><ShieldCheck :size="13" />{{
                      mode === "business"
                        ? "以当前账户身份查询"
                        : "仅引用本次检索资料"
                    }}</span
                  ><button
                    v-if="sending"
                    type="button"
                    class="send stop"
                    aria-label="停止回答"
                    @click="activeRequest?.abort()"
                  >
                    <Square :size="15" /></button
                  ><button
                    v-else
                    class="send"
                    :disabled="!question.trim() || switching"
                    aria-label="发送消息"
                  >
                    <ArrowUp :size="19" />
                  </button>
                </div>
              </form>
              <p class="composer-footnote">
                Enter 发送 · Shift + Enter 换行<span>{{
                  settings?.mode === "openai"
                    ? "业务查询使用真实 API · 范围外问题直接提示"
                    : "当前使用本地替身"
                }}</span>
              </p>
            </div>
          </div>
          <aside v-if="sending || activeTrace" class="execution-sidebar">
            <div class="execution-heading">
              <Workflow :size="17" /><strong>执行现场</strong
              ><span v-if="sending" class="live-badge"><i></i>进行中</span>
            </div>
            <p>由服务器记录的模型与工具步骤。</p>
            <TraceView
              :key="sending ? 'live' : activeTrace?.traceId"
              :trace="sending ? liveTrace : activeTrace!"
              :live="sending"
              expanded
              @gateway="openGateway"
            /><button
              v-if="admin && !sending"
              class="secondary wide"
              @click="openGateway(activeTrace?.traceId)"
            >
              <Network :size="16" />在 Gateway 中查看
            </button>
          </aside>
        </div>
      </section>
      <section v-else-if="page === 'knowledge'" class="content-page">
        <header class="page-heading">
          <div>
            <div class="overline">SHOPGUARD / KNOWLEDGE</div>
            <h1>知识库</h1>
            <p>维护平台规则，让 AI 回答可以回到原文。</p>
          </div>
          <button
            v-if="admin"
            class="primary"
            :disabled="importing"
            @click="showImport = true"
          >
            <Upload :size="16" />导入文档
          </button>
        </header>
        <div class="knowledge-banner">
          <span class="knowledge-banner-icon"><BookOpen :size="26" /></span>
          <div>
            <strong>平台公共规则</strong>
            <p>
              {{ docs.length }} 份文档 · {{ settings?.knowledgeStore }} ·
              {{ settings?.embeddingModel }}
            </p>
          </div>
          <button class="secondary" @click="newSession('knowledge')">
            规则问答 <ArrowUpRight :size="15" />
          </button>
        </div>
        <div class="section-bar">
          <h2>
            全部文档 <span class="count">{{ docs.length }}</span>
          </h2>
          <button
            v-if="admin"
            class="text-button"
            :disabled="importing"
            @click="importSample"
          >
            {{ importing ? "正在处理…" : "导入示例规则" }}
          </button>
        </div>
        <div v-if="loading" class="loading-state">正在读取…</div>
        <div v-else-if="!docs.length" class="empty-state panel">
          <BookOpen :size="36" />
          <h3>添加第一份规则文档</h3>
          <p>
            {{
              admin
                ? "导入 Markdown、文本文件，或直接粘贴内容。"
                : "请联系管理员导入平台规则。"
            }}
          </p>
        </div>
        <div v-else class="document-grid">
          <article v-for="doc in docs" :key="doc.id" class="document-card">
            <div class="doc-top">
              <span class="document-icon"><FileText :size="23" /></span
              ><span class="pill success">可检索</span>
            </div>
            <h3>{{ doc.title }}</h3>
            <p>{{ doc.chunks }} 个片段 · {{ doc.embeddingModel }}</p>
            <div class="doc-footer">
              <time>{{ date(doc.createdAt) }}</time
              ><button
                v-if="admin"
                class="icon-button"
                :aria-label="'删除文档 ' + doc.title"
                @click="
                  pendingDelete = {
                    kind: 'document',
                    id: doc.id,
                    title: doc.title,
                  }
                "
              >
                <Trash2 :size="15" />
              </button>
            </div>
          </article>
        </div>
        <p class="page-footnote">
          导入与规则检索会调用 Embedding API。引用内容来自实际检索到的文档片段。
        </p>
      </section>
      <section v-else class="content-page">
        <header class="page-heading">
          <div>
            <div class="overline">WORKSPACE / CONNECTIONS</div>
            <h1>连接与配置</h1>
            <p>查看当前运行模式和服务连接。</p>
          </div>
          <button v-if="admin" class="secondary" @click="loadPage('settings')">
            <RefreshCw :size="15" />检查连接
          </button>
        </header>
        <div class="settings-grid">
          <article class="panel settings-card">
            <span class="settings-icon"><Cpu :size="25" /></span>
            <h2>
              {{ settings?.mode === "openai" ? "OpenAI API" : "本地替身" }}
            </h2>
            <span
              class="pill"
              :class="settings?.mode === 'openai' ? 'success' : 'neutral'"
              >{{
                settings?.mode === "openai" ? "真实调用模式" : "本地验证模式"
              }}</span
            >
            <dl>
              <div>
                <dt>逻辑模型</dt>
                <dd>{{ settings?.model }}</dd>
              </div>
              <div>
                <dt>向量模型</dt>
                <dd>{{ settings?.embeddingModel }}</dd>
              </div>
              <div>
                <dt>调用入口</dt>
                <dd>LLM Gateway</dd>
              </div>
            </dl>
            <p>发送对话与导入文档时使用服务器上的现有 API 配置。</p>
          </article>
          <article class="panel settings-card">
            <span class="settings-icon"><Layers3 :size="25" /></span>
            <h2>数据与会话</h2>
            <span class="pill neutral">持久化存储</span>
            <dl>
              <div>
                <dt>知识索引</dt>
                <dd>{{ settings?.knowledgeStore }}</dd>
              </div>
              <div>
                <dt>会话有效期</dt>
                <dd>{{ settings?.sessionMinutes }} 分钟无操作</dd>
              </div>
              <div>
                <dt>知识范围</dt>
                <dd>平台公共规则</dd>
              </div>
            </dl>
            <p>订单、聊天和知识资料保存在服务器，退出登录后仍然保留。</p>
          </article>
        </div>
        <article v-if="admin && health" class="panel health-panel">
          <h2>本地服务状态</h2>
          <div>
            <span>Gateway</span
            ><strong>{{
              (health.gateway as { available?: boolean })?.available === false
                ? "连接失败"
                : "可连接"
            }}</strong>
          </div>
          <div>
            <span>{{ settings?.knowledgeStore }}</span
            ><strong>{{ health.vectorStore ? "可连接" : "连接失败" }}</strong>
          </div>
          <div>
            <span>Kafka 用量事件</span
            ><strong>{{
              (health.gateway as { eventsEnabled?: boolean })?.eventsEnabled
                ? "已启用"
                : "当前模式未启用"
            }}</strong>
          </div>
          <p class="muted">连接检查不发送模型请求，不消耗模型 Token。</p>
        </article>
      </section>
    </main>
    <div
      v-if="traceModal"
      class="drawer-backdrop"
      @click.self="traceModal = false"
    >
      <aside
        class="request-drawer"
        role="dialog"
        aria-modal="true"
        aria-label="AI 执行过程"
      >
        <div class="detail-top">
          <strong>执行过程</strong
          ><button
            class="icon-button"
            aria-label="关闭执行过程"
            @click="traceModal = false"
          >
            <X :size="20" />
          </button>
        </div>
        <TraceView
          :trace="sending ? liveTrace : activeTrace || {}"
          :live="sending"
          expanded
          @gateway="openGateway"
        />
      </aside>
    </div>
    <div
      v-if="pendingDelete"
      class="modal-backdrop"
      @click.self="pendingDelete = null"
    >
      <section
        class="modal small"
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-title"
      >
        <h2 id="delete-title">
          删除{{ pendingDelete.kind === "session" ? "会话" : "文档" }}？
        </h2>
        <p>
          “{{ pendingDelete.title }}”将被删除。{{
            pendingDelete.kind === "document"
              ? "删除后不再参与知识检索。"
              : "会话中的消息也会删除。"
          }}
        </p>
        <div class="modal-actions">
          <button class="secondary" @click="pendingDelete = null">取消</button
          ><button class="danger-button" @click="removeConfirmed">
            确认删除
          </button>
        </div>
      </section>
    </div>
    <div
      v-if="showImport"
      class="modal-backdrop"
      @click.self="!importing && (showImport = false)"
    >
      <form
        class="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-title"
        @submit.prevent="importDocument"
      >
        <div class="modal-top">
          <h2 id="import-title">导入知识文档</h2>
          <button
            type="button"
            class="icon-button"
            :disabled="importing"
            aria-label="关闭导入"
            @click="showImport = false"
          >
            <X :size="19" />
          </button>
        </div>
        <p>
          支持 UTF-8 的 .md、.txt，或直接粘贴。提交会调用已配置的 Embedding
          API。
        </p>
        <label class="file-input"
          ><Upload :size="18" />选择文本文件<input
            type="file"
            accept=".md,.txt,text/plain,text/markdown"
            @change="loadFile" /></label
        ><label for="doc-title">文档标题</label
        ><input
          id="doc-title"
          v-model="docTitle"
          required
          maxlength="160"
          placeholder="例如：校园交易规则"
        /><label for="doc-content">文档内容</label
        ><textarea
          id="doc-content"
          v-model="docContent"
          required
          maxlength="20000"
          rows="10"
          placeholder="粘贴需要检索的规则正文…"
        ></textarea>
        <div class="modal-actions">
          <span class="muted">{{ docContent.length }} / 20,000</span
          ><button
            class="primary"
            :disabled="importing || !docTitle.trim() || !docContent.trim()"
          >
            {{ importing ? "正在向量化…" : "导入并建立索引" }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
