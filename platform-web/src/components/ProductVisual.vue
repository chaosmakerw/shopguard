<script setup lang="ts">
import { computed } from "vue";
import {
  LampDesk,
  Headphones,
  Keyboard,
  Smartphone,
  Monitor,
  Package,
} from "@lucide/vue";
const props = defineProps<{
  title: string;
  category?: string;
  compact?: boolean;
}>();
const book = computed(
  () =>
    props.category === "书籍" || /Java|数据库|书|算法|程序/.test(props.title),
);
const hue = computed(
  () =>
    [...props.title].reduce((value, char) => value + char.charCodeAt(0), 0) % 4,
);
const objectIcon = computed(() =>
  /灯/.test(props.title)
    ? LampDesk
    : /键盘/.test(props.title)
      ? Keyboard
      : /耳机/.test(props.title)
        ? Headphones
        : /手机/.test(props.title)
          ? Smartphone
          : props.category === "数码"
            ? Monitor
            : Package,
);
</script>
<template>
  <div
    class="product-visual"
    :class="['palette-' + hue, { compact }]"
    aria-hidden="true"
  >
    <div v-if="book" class="book-cover">
      <span class="book-spine"></span
      ><span class="book-caption">READ / LEARN</span><strong>{{ title }}</strong
      ><span class="cover-rule"></span
      ><span class="cover-edition">书籍 · 校园好物</span><i></i>
    </div>
    <div v-else class="object-scene">
      <div class="object-shadow"></div>
      <component
        :is="objectIcon"
        :size="compact ? 30 : 88"
        :stroke-width="1.15"
      /><span class="object-label">{{ category || "生活好物" }}</span>
    </div>
  </div>
</template>
