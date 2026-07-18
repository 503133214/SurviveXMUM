<template>
  <div class="abp">
    <header class="abp-head"><h1>发布公告</h1></header>
    <p class="abp-note">公告将以站内通知的形式发送给<strong>全体正常用户</strong>（顶栏铃铛可见）。发送后不可撤回，请仔细核对。</p>

    <div class="abp-card">
      <el-form label-width="72px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="如：本周六凌晨系统维护公告" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="500" show-word-limit
                    placeholder="公告正文（最多 500 字）" />
        </el-form-item>
        <el-form-item label="链接">
          <el-input v-model="form.link" maxlength="400" placeholder="可选，点击通知跳转的站内路径，如 /docs/贡献指南" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="sending" @click="send">发送公告</el-button>
          <span v-if="lastResult" class="abp-result">{{ lastResult }}</span>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminBroadcast } from '@/net/index.js'

export default {
  name: 'AdminBroadcastPanel',
  data() {
    return { form: { title: '', content: '', link: '' }, sending: false, lastResult: '' }
  },
  methods: {
    async send() {
      const title = this.form.title.trim()
      const content = this.form.content.trim()
      if (!title) return ElMessage.warning('请填写公告标题')
      if (!content) return ElMessage.warning('请填写公告内容')
      try {
        await ElMessageBox.confirm(
          `将向全体用户发送公告《${title}》，发送后不可撤回。确认发送？`,
          '确认发送', { confirmButtonText: '发送', cancelButtonText: '再检查一下', type: 'warning' })
      } catch { return }
      this.sending = true
      adminBroadcast(
        { title, content, link: this.form.link.trim() || null },
        (d) => {
          this.sending = false
          const n = Number(d && d.sent) || 0
          this.lastResult = `已发送给 ${n} 位用户（${new Date().toLocaleTimeString()}）`
          ElMessage.success(`公告已发送给 ${n} 位用户`)
          this.form = { title: '', content: '', link: '' }
        },
        (m) => { this.sending = false; ElMessage.error(m || '发送失败') })
    },
  },
}
</script>

<style scoped>
.abp-head h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 8px; color: var(--text-primary); }
.abp-note { margin: 0 0 18px; color: var(--text-muted); font-size: 13px; }
.abp-note strong { color: var(--text-secondary); }
.abp-card {
  max-width: 640px;
  padding: 20px 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}
.abp-result { margin-left: 12px; color: var(--text-muted); font-size: 13px; }
@media (max-width: 640px) {
  .abp-card {
    max-width: none;
    padding: 18px 14px;
  }
  .abp-card :deep(.el-form-item) {
    display: block;
  }
  .abp-card :deep(.el-form-item__label) {
    width: auto !important;
    height: auto;
    margin-bottom: 7px;
    line-height: 1.4;
  }
  .abp-card :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
  .abp-card :deep(.el-button) {
    width: 100%;
    margin: 0;
  }
  .abp-result {
    display: block;
    width: 100%;
    margin: 10px 0 0;
    overflow-wrap: anywhere;
  }
}
</style>
