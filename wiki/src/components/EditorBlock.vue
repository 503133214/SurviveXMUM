<template>
    <div class="editor-wrapper">
      <div ref="editorHolder" class="editor-container" />
      <div class="action-buttons">
        <el-button type="primary" @click="emitMarkdown">导出 Markdown</el-button>
        <el-button type="danger" @click="clearContent">清空内容</el-button>
      </div>
    </div>
  </template>
  
  <script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import EditorJS from '@editorjs/editorjs'
import Header from '@editorjs/header'
import Paragraph from '@editorjs/paragraph'
import List from '@editorjs/list'
import ImageTool from '@editorjs/image'
import CodeTool from '@editorjs/code'
import Quote from '@editorjs/quote'
import Delimiter from '@editorjs/delimiter'
import Table from '@editorjs/table'
import Parser from 'editorjs-parser'
  
  const emit = defineEmits(['save'])
  const editorHolder = ref(null)
  const editorInstance = ref(null)
  const parser = new Parser() 
  
  onMounted(() => {
    editorInstance.value = new EditorJS({
      holder: editorHolder.value,
      tools: {
      header: Header,
      paragraph: Paragraph,
      list: List,
      delimiter: Delimiter,
      code: CodeTool,
      quote: Quote,
      table: Table,
      image: {
        class: ImageTool,
        config: {
          endpoints: {
            byFile: '/api/upload/file',   //需要提供后端上传接口
            byUrl: '/api/upload/url'     // 支持粘贴图片链接
          },
          field: 'image', // POST 表单字段名
          captionPlaceholder: '输入图片说明',
        }
      }
    },
    autofocus: true,
      placeholder: '请输入内容...',
    })
  })
  
  onBeforeUnmount(() => {
    editorInstance.value?.destroy()
  })
  
  const emitMarkdown = async () => {
    const data = await editorInstance.value.save()
    const markdown = parser.parse(data, 'markdown')
    emit('update:modelValue', markdown)
    emit('save', markdown)
  }

  const clearContent = async () => {
  await editorInstance.value.clear()
  emit('update:modelValue', '')
}

  </script>
  
  <style scoped>
  .editor-wrapper {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 16px;
  background-color: #fafafa;
}
.editor-container {
  min-height: 300px;
}
.action-buttons {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  justify-content: flex-end;
}
  </style>
  