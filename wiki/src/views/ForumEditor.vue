<template>
    <div style="padding: 24px;">
      <h1>论坛编辑器</h1>
      <EditorBlock @save="onSave" />
      <h3 style="margin-top: 24px;">导出的 Markdown：</h3>
    <el-input
      type="textarea"
      v-model="markdownText"
      rows="12"
      readonly
    />

    <div style="margin-top: 16px;">
      <MarkdownDownload :markdown="markdownText" filename="我的文章.md" />
    </div>

    </div>
  </template>
  
  <script setup>
  import { ref } from 'vue'
  import EditorBlock from '@/components/EditorBlock.vue'
  import MarkdownDownload from '@/components/MarkdownDownload.vue'
  
  const markdownText = ref('')

  const onSave = (md) => {
  markdownText.value = md
  console.log('导出的 Markdown 内容：', md)
}
  </script>
  