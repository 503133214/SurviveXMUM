<template>
    <el-button type="success" @click="downloadMarkdown" :disabled="!markdown">
      下载 Markdown
    </el-button>
  </template>
  
  <script setup>
  import { defineProps } from 'vue'
  
  const props = defineProps({
    markdown: {
      type: String,
      required: true
    },
    filename: {
      type: String,
      default: 'article.md'
    }
  })
  
  const downloadMarkdown = () => {
    const blob = new Blob([props.markdown], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = props.filename
    a.click()
    URL.revokeObjectURL(url)
  }
  </script>
  