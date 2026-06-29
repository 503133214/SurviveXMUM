<template>
  <div class="afp">
    <header class="afp-head">
      <h1>反馈管理</h1>
      <div class="afp-filter">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="load" @clear="load">
          <el-option label="待处理" value="pending" />
          <el-option label="处理中" value="processing" />
          <el-option label="已解决" value="resolved" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索标题 / 内容"
          clearable
          style="width: 220px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button @click="load">查询</el-button>
      </div>
    </header>

    <el-table :data="list" v-loading="loading" style="width: 100%">
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.type)">{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="提交人" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.userEmail || '—' }}</template>
      </el-table-column>
      <el-table-column label="评分" width="120">
        <template #default="{ row }">
          <el-rate v-if="row.rating" :model-value="row.rating" disabled size="small" />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" width="150" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openReply(row)">查看 / 回复</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !list.length" description="暂无反馈" />

    <el-dialog v-model="dialogVisible" title="反馈详情" width="560px">
      <div v-if="current" class="afp-detail">
        <div class="afp-row">
          <span class="k">类型</span>
          <el-tag size="small" :type="typeTag(current.type)">{{ typeText(current.type) }}</el-tag>
        </div>
        <div class="afp-row"><span class="k">标题</span><b>{{ current.title }}</b></div>
        <div class="afp-row"><span class="k">提交人</span>{{ current.userEmail || '—' }}</div>
        <div class="afp-row" v-if="current.contact"><span class="k">联系方式</span>{{ current.contact }}</div>
        <div class="afp-row" v-if="current.rating">
          <span class="k">满意度</span>
          <el-rate :model-value="current.rating" disabled size="small" />
        </div>
        <div class="afp-row"><span class="k">提交时间</span>{{ current.createTime }}</div>
        <div class="afp-content">{{ current.content }}</div>

        <el-divider content-position="left">处理</el-divider>
        <el-form label-width="72px">
          <el-form-item label="状态">
            <el-select v-model="replyStatus" style="width: 160px">
              <el-option label="待处理" value="pending" />
              <el-option label="处理中" value="processing" />
              <el-option label="已解决" value="resolved" />
              <el-option label="已驳回" value="rejected" />
            </el-select>
          </el-form-item>
          <el-form-item label="回复">
            <el-input
              v-model="replyText"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="回复内容（提交人可见，可选）"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReply">保存并通知</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { adminListFeedback, adminReplyFeedback } from '@/net/index.js'

export default {
  name: 'AdminFeedbackPanel',
  data() {
    return {
      status: '',
      keyword: '',
      list: [],
      loading: false,
      dialogVisible: false,
      current: null,
      replyText: '',
      replyStatus: 'resolved',
      saving: false,
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    typeText(t) { return { bug: '问题', feature: '建议', ui: '界面', other: '其他' }[t] || t },
    typeTag(t) { return { bug: 'danger', feature: 'success', ui: 'warning', other: 'info' }[t] || 'info' },
    statusText(s) { return { pending: '待处理', processing: '处理中', resolved: '已解决', rejected: '已驳回' }[s] || s },
    statusTag(s) { return { pending: 'info', processing: 'warning', resolved: 'success', rejected: 'danger' }[s] || 'info' },
    load() {
      this.loading = true
      adminListFeedback(
        { status: this.status, keyword: this.keyword },
        (data) => { this.list = data || []; this.loading = false },
        (msg) => { this.loading = false; ElMessage.error(msg || '加载失败') }
      )
    },
    openReply(row) {
      this.current = row
      this.replyText = row.reply || ''
      this.replyStatus = row.status === 'pending' ? 'resolved' : row.status
      this.dialogVisible = true
    },
    submitReply() {
      this.saving = true
      adminReplyFeedback(
        this.current.id,
        { reply: this.replyText, status: this.replyStatus },
        () => {
          this.saving = false
          this.dialogVisible = false
          ElMessage.success('已保存并通知用户')
          this.load()
        },
        (msg) => { this.saving = false; ElMessage.error(msg || '操作失败') }
      )
    },
  },
}
</script>

<style scoped>
.afp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.afp-head h1 {
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin: 0;
  color: var(--text-primary);
}
.afp-filter { display: flex; gap: 10px; flex-wrap: wrap; }
.afp-detail .afp-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--text-secondary);
}
.afp-detail .afp-row .k { width: 64px; color: var(--text-muted); flex-shrink: 0; }
.afp-content {
  margin-top: 8px;
  padding: 12px;
  border-radius: 8px;
  background: var(--bg-subtle);
  white-space: pre-wrap;
  line-height: 1.6;
  color: var(--text-primary);
}
.muted { color: var(--text-muted); }
@media (max-width: 768px) {
  .afp-head { align-items: flex-start; }
  .afp-filter { width: 100%; }
}
</style>
