<script setup lang="ts">
import {ref} from 'vue'
import {request,type Citation} from './api'
const props=defineProps<{citation:Citation;index:number}>()
const text=ref(''),error=ref(''),loading=ref(false)
async function expand(event:Event){
  if(!(event.target as HTMLDetailsElement).open||text.value||loading.value||!props.citation.chunkId)return
  loading.value=true;error.value=''
  try{text.value=(await request<{text:string}>(`/knowledge/chunks/${encodeURIComponent(props.citation.chunkId)}`)).text}
  catch(e){error.value=e instanceof Error?e.message:'无法读取引用资料'}finally{loading.value=false}
}
</script>
<template><details @toggle="expand"><summary>{{ index+1 }} · {{ citation.source }}</summary><p>{{ citation.section }} · 版本 {{ citation.version }}</p><p v-if="loading">正在读取原文…</p><p v-else-if="error">{{ error }}</p><blockquote v-else-if="text" style="white-space:pre-wrap;margin:12px 0;line-height:1.8">{{ text }}</blockquote></details></template>
