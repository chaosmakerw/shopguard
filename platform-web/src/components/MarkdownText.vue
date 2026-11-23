<script setup lang="ts">
import { computed } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
const props = defineProps<{ text: string }>();
const html = computed(() =>
  DOMPurify.sanitize(
    marked.parse(props.text, {
      async: false,
      breaks: true,
      gfm: true,
    }) as string,
    {
      USE_PROFILES: { html: true },
      FORBID_ATTR: ["style", "class", "id", "name"],
      FORBID_TAGS: [
        "img",
        "input",
        "form",
        "style",
        "video",
        "audio",
        "iframe",
      ],
    },
  ),
);
</script>
<template><div class="markdown message-text" v-html="html"></div></template>
