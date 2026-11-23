<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  Plus,
  Search,
  RefreshCw,
  X,
  Package,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Save,
  Boxes,
  History,
} from "@lucide/vue";
import { request, post } from "../api";
import {
  categories,
  money,
  date,
  errorText,
  type CatalogProduct,
  type PageResult,
} from "../commerce";
import ProductVisual from "./ProductVisual.vue";
const result = ref<PageResult<CatalogProduct>>({
    items: [],
    total: 0,
    page: 1,
    size: 12,
  }),
  query = ref(""),
  category = ref(""),
  state = ref("all"),
  page = ref(1),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const editor = ref(false),
  selected = ref<CatalogProduct | null>(null),
  stockItem = ref<CatalogProduct | null>(null),
  delta = ref(1),
  reason = ref(""),
  history = ref<{ quantityDelta: number; reason: string; createdAt: number }[]>(
    [],
  );
const form = ref({
  title: "",
  description: "",
  price: "",
  category: "书籍",
  available: true,
  version: 0,
  initialStock: 0,
});
let createKey = "";
async function load() {
  loading.value = true;
  try {
    result.value = await request(
      "/admin/commerce/products?" +
        new URLSearchParams({
          keyword: query.value,
          category: category.value,
          state: state.value,
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
function edit(item?: CatalogProduct) {
  selected.value = item || null;
  form.value = item
    ? {
        title: item.title,
        description: item.description,
        price: item.price,
        category: item.category,
        available: item.available,
        version: item.version,
        initialStock: 0,
      }
    : {
        title: "",
        description: "",
        price: "",
        category: "书籍",
        available: true,
        version: 0,
        initialStock: 0,
      };
  createKey = crypto.randomUUID();
  error.value = "";
  editor.value = true;
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await request(
      selected.value
        ? "/admin/commerce/products/" + selected.value.productId
        : "/admin/commerce/products",
      {
        method: "POST",
        headers: { "Idempotency-Key": createKey },
        body: JSON.stringify(form.value),
      },
    );
    editor.value = false;
    notice.value = selected.value ? "商品已更新" : "商品已建档";
    await load();
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
async function openStock(item: CatalogProduct) {
  stockItem.value = item;
  delta.value = 1;
  reason.value = "";
  error.value = "";
  try {
    history.value = await request(
      "/admin/commerce/products/" + item.productId + "/stock",
    );
  } catch (e) {
    error.value = errorText(e);
  }
}
async function adjust() {
  if (!stockItem.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await post(
      "/admin/commerce/products/" + stockItem.value.productId + "/stock",
      { delta: delta.value, reason: reason.value },
    );
    notice.value = "库存已更新，调整记录已保存";
    stockItem.value = null;
    await load();
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="content-page catalog-page">
    <header class="page-heading">
      <div>
        <h1>商品与库存</h1>
      </div>
      <button class="primary" @click="edit()">
        <Plus :size="17" />新增商品
      </button>
    </header>
    <div v-if="error && !editor && !stockItem" class="alert error" role="alert">
      {{ error }}
    </div>
    <div v-if="notice" class="alert success" role="status">
      {{ notice
      }}<button @click="notice = ''" aria-label="关闭提示">
        <X :size="16" />
      </button>
    </div>
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
            aria-label="搜索管理商品"
            placeholder="搜索商品名称或描述"
          /><button type="submit">搜索</button>
        </form>
        <select
          v-model="category"
          aria-label="商品分类"
          @change="
            page = 1;
            load();
          "
        >
          <option value="">全部分类</option>
          <option v-for="item in categories" :key="item">
            {{ item }}
          </option></select
        ><select
          v-model="state"
          aria-label="商品状态"
          @change="
            page = 1;
            load();
          "
        >
          <option value="all">全部状态</option>
          <option value="active">已上架</option>
          <option value="inactive">已下架</option>
          <option value="low">低库存</option></select
        ><button
          class="icon-button"
          aria-label="刷新商品"
          :disabled="loading"
          @click="load"
        >
          <RefreshCw :size="17" :class="{ spin: loading }" />
        </button>
      </div>
      <div class="table-scroll">
        <table class="business-table product-table">
          <thead>
            <tr>
              <th>商品</th>
              <th>分类</th>
              <th>售价</th>
              <th>可售 / 预占 / 已售</th>
              <th>状态</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in result.items" :key="item.productId">
              <td>
                <div class="table-product">
                  <ProductVisual
                    :title="item.title"
                    :category="item.category"
                    compact
                  />
                  <div>
                    <strong>{{ item.title }}</strong
                    ><small>{{ item.productId }}</small>
                  </div>
                </div>
              </td>
              <td>
                <span class="category-tag">{{ item.category }}</span>
              </td>
              <td class="numeric">¥ {{ money(item.price) }}</td>
              <td>
                <div class="stock-numbers">
                  <b :class="{ warning: item.availableQuantity <= 3 }">{{
                    item.availableQuantity
                  }}</b
                  ><span
                    >/ {{ item.reservedQuantity }} /
                    {{ item.soldQuantity }}</span
                  >
                </div>
              </td>
              <td>
                <span
                  class="status-label"
                  :class="item.available ? 'paid' : 'cancelled'"
                  >{{ item.available ? "已上架" : "已下架" }}</span
                >
              </td>
              <td>
                <div class="row-actions">
                  <button @click="openStock(item)">
                    <Boxes :size="14" />调库存</button
                  ><button @click="edit(item)">
                    编辑<ArrowUpRight :size="14" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!result.items.length" class="empty-panel">
          <Package :size="32" />
          <h3>{{ loading ? "正在读取商品" : "没有匹配的商品" }}</h3>
          <p>调整筛选条件，或新增一个商品。</p>
        </div>
      </div>
      <footer class="table-pagination">
        <span>共 {{ result.total }} 件商品</span>
        <div>
          <button
            class="icon-button"
            aria-label="管理商品上一页"
            :disabled="page === 1 || loading"
            @click="
              page--;
              load();
            "
          >
            <ChevronLeft :size="17" /></button
          ><span
            >{{ page }} / {{ Math.max(1, Math.ceil(result.total / 12)) }}</span
          ><button
            class="icon-button"
            aria-label="管理商品下一页"
            :disabled="page * 12 >= result.total || loading"
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
      v-if="editor"
      class="commerce-modal-backdrop"
      @click.self="!busy && (editor = false)"
    >
      <section
        class="commerce-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="product-editor-title"
      >
        <header>
          <div>
            <span class="section-kicker">商品资料</span>
            <h2 id="product-editor-title">
              {{ selected ? "编辑商品" : "新增商品" }}
            </h2>
          </div>
          <button
            class="icon-button"
            aria-label="关闭商品编辑"
            :disabled="busy"
            @click="editor = false"
          >
            <X :size="20" />
          </button>
        </header>
        <form @submit.prevent="save">
          <div v-if="error" class="alert error" role="alert">{{ error }}</div>
          <label
            >商品名称<input
              v-model="form.title"
              required
              maxlength="120"
              placeholder="例如：机械键盘 · 87 键"
          /></label>
          <div class="form-columns">
            <label
              >分类<select v-model="form.category" aria-label="分类">
                <option v-for="item in categories" :key="item">
                  {{ item }}
                </option>
              </select></label
            ><label
              >售价（元）<input
                v-model="form.price"
                type="number"
                step="0.01"
                min="0.01"
                max="9999999999.99"
                required
            /></label>
          </div>
          <label
            >商品描述<textarea
              v-model="form.description"
              rows="4"
              required
              maxlength="1000"
              placeholder="说明商品情况、规格和交易须知"
            ></textarea>
          </label>
          <div class="form-columns">
            <label v-if="!selected"
              >初始库存<input
                v-model.number="form.initialStock"
                type="number"
                min="0"
                max="100000"
                step="1"
                required /></label
            ><label
              >销售状态<select v-model="form.available" aria-label="销售状态">
                <option :value="true">上架销售</option>
                <option :value="false">暂不上架</option>
              </select></label
            >
          </div>
          <p v-if="selected" class="form-note">
            修改售价不会改变已有订单的成交金额。库存请通过“调库存”入口维护。
          </p>
          <footer>
            <button
              type="button"
              class="secondary"
              :disabled="busy"
              @click="editor = false"
            >
              取消</button
            ><button class="primary" :disabled="busy">
              <Save :size="16" />{{ busy ? "保存中…" : "保存商品" }}
            </button>
          </footer>
        </form>
      </section>
    </div>
    <div
      v-if="stockItem"
      class="commerce-modal-backdrop"
      @click.self="!busy && (stockItem = null)"
    >
      <section
        class="commerce-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="stock-title"
      >
        <header>
          <div>
            <span class="section-kicker">{{ stockItem.title }}</span>
            <h2 id="stock-title">调整库存</h2>
          </div>
          <button
            class="icon-button"
            aria-label="关闭库存调整"
            :disabled="busy"
            @click="stockItem = null"
          >
            <X :size="20" />
          </button>
        </header>
        <div class="stock-preview">
          <div>
            <strong>{{ stockItem.availableQuantity }}</strong
            ><span>当前可售</span>
          </div>
          <div>
            <strong>{{ stockItem.reservedQuantity }}</strong
            ><span>订单预占</span>
          </div>
          <div>
            <strong>{{ stockItem.soldQuantity }}</strong
            ><span>已售出</span>
          </div>
        </div>
        <form @submit.prevent="adjust">
          <div v-if="error" class="alert error" role="alert">{{ error }}</div>
          <label
            >调整数量<input
              v-model.number="delta"
              aria-label="调整数量"
              type="number"
              step="1"
              min="-100000"
              max="100000"
              required
            /><small>正数补货，负数扣减；订单预占库存不受影响。</small></label
          ><label
            >调整原因<input
              v-model="reason"
              maxlength="200"
              required
              placeholder="例如：采购入库、盘点修正"
          /></label>
          <div v-if="history.length" class="stock-history">
            <h3><History :size="15" />最近调整</h3>
            <div v-for="(entry, index) in history.slice(0, 4)" :key="index">
              <strong
                >{{ entry.quantityDelta > 0 ? "+" : ""
                }}{{ entry.quantityDelta }}</strong
              ><span>{{ entry.reason }}</span
              ><small>{{ date(entry.createdAt) }}</small>
            </div>
          </div>
          <footer>
            <button
              type="button"
              class="secondary"
              :disabled="busy"
              @click="stockItem = null"
            >
              取消</button
            ><button
              class="primary"
              :disabled="busy || !Number.isInteger(delta) || delta === 0"
            >
              {{ busy ? "提交中…" : "确认调整" }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
