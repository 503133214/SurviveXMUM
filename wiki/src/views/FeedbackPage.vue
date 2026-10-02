<template>
  <div class="feedback-page">
    <header class="fb-head">
      <h1>意见反馈</h1>
      <p class="fb-sub">遇到问题或有建议，写在这里，管理员会在下方回复。</p>
    </header>

    <!-- 表单项名称统一放在输入框上方：手机和桌面同一种排版，不用再为窄屏改写 label 宽度 -->
    <el-form
      ref="feedbackFormRef"
      :model="feedbackForm"
      :rules="feedbackRules"
      label-position="top"
      class="fb-form"
    >
      <el-form-item label="反馈类型" prop="type">
        <el-radio-group v-model="feedbackForm.type">
          <el-radio value="bug">问题反馈</el-radio>
          <el-radio value="feature">功能建议</el-radio>
          <el-radio value="ui">界面优化</el-radio>
          <el-radio value="other">其他</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="反馈标题" prop="title">
        <el-input
          v-model="feedbackForm.title"
          placeholder="一句话说明是什么问题或建议"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="详细描述" prop="content">
        <el-input
          v-model="feedbackForm.content"
          type="textarea"
          :rows="6"
          placeholder="写清楚在哪个页面、做了什么、看到了什么，或者你希望怎么改"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="满意度（选填）" prop="rating">
        <el-rate
          v-model="feedbackForm.rating"
          show-text
          :texts="['非常不满意', '不满意', '一般', '满意', '非常满意']"
        />
      </el-form-item>

      <el-form-item label="联系方式" prop="contact">
        <el-input
          v-model="feedbackForm.contact"
          placeholder="选填，方便管理员联系你（邮箱或手机号）"
        />
      </el-form-item>

      <el-form-item class="fb-actions">
        <el-button type="primary" @click="submitFeedback" :loading="isSubmitting">
          提交反馈
        </el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>

    <section v-if="myFeedbacks.length" class="fb-history">
      <h2 class="fb-section-title">我的反馈记录</h2>

      <el-timeline>
        <el-timeline-item
          v-for="item in myFeedbacks"
          :key="item.id"
          :timestamp="item.createTime"
          :type="item.status === 'resolved' ? 'success' : item.status === 'processing' ? 'warning' : 'info'"
        >
          <div class="feedback-item">
            <div class="feedback-item-header">
              <h3>{{ item.title }}</h3>
              <el-tag :type="statusType(item.status)">{{ statusText(item.status) }}</el-tag>
            </div>
            <p class="feedback-content">{{ item.content }}</p>
            <div v-if="item.reply" class="feedback-reply">
              <p class="reply-label">管理员回复</p>
              <p class="reply-body">{{ item.reply }}</p>
              <span class="reply-time">回复于 {{ item.replyTime }}</span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </section>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post } from '@/net/index.js'

export default {
  name: 'FeedbackPage',
  setup() {
    const feedbackFormRef = ref(null)
    const isSubmitting = ref(false)
    const myFeedbacks = ref([])

    const feedbackForm = reactive({
      type: 'bug',
      title: '',
      content: '',
      rating: 0,
      contact: ''
    })

    const feedbackRules = {
      type: [
        { required: true, message: '请选择反馈类型', trigger: 'change' }
      ],
      title: [
        { required: true, message: '请输入反馈标题', trigger: 'blur' },
        { min: 2, max: 50, message: '标题长度应为2-50个字符', trigger: 'blur' }
      ],
      content: [
        { required: true, message: '请输入详细描述', trigger: 'blur' },
        { min: 10, max: 1000, message: '描述长度应为10-1000个字符', trigger: 'blur' }
      ]
    }

    const statusText = (status) => {
      const map = { pending: '待处理', processing: '处理中', resolved: '已解决', rejected: '已驳回' }
      return map[status] || status
    }

    const statusType = (status) => {
      const map = { pending: 'info', processing: 'warning', resolved: 'success', rejected: 'danger' }
      return map[status] || 'info'
    }

    const submitFeedback = () => {
      feedbackFormRef.value.validate((valid) => {
        if (!valid) return
        isSubmitting.value = true
        post('/feedback',
          {
            type: feedbackForm.type,
            title: feedbackForm.title,
            content: feedbackForm.content,
            rating: feedbackForm.rating,
            contact: feedbackForm.contact
          },
          () => {
            isSubmitting.value = false
            ElMessage.success('反馈已提交，管理员回复后会显示在下方')
            resetForm()
            loadFeedbacks()
          },
          (message) => {
            isSubmitting.value = false
            ElMessage.error(message || '提交失败，请稍后重试')
          }
        )
      })
    }

    const resetForm = () => {
      feedbackForm.type = 'bug'
      feedbackForm.title = ''
      feedbackForm.content = ''
      feedbackForm.rating = 0
      feedbackForm.contact = ''
      if (feedbackFormRef.value) {
        feedbackFormRef.value.resetFields()
      }
    }

    const loadFeedbacks = () => {
      get('/feedback/my',
        (data) => {
          myFeedbacks.value = data || []
        },
        () => {
          myFeedbacks.value = []
        }
      )
    }

    onMounted(() => {
      loadFeedbacks()
    })

    return {
      feedbackFormRef,
      feedbackForm,
      feedbackRules,
      isSubmitting,
      myFeedbacks,
      statusText,
      statusType,
      submitFeedback,
      resetForm
    }
  }
}
</script>

<style scoped>
/* 与其它应用页同一套页头和留白；表单直接铺在页面上，不再套 el-card */
.feedback-page {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 40px 20px 64px;
}

.fb-head { margin-bottom: 24px; }
.fb-head h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
}
.fb-sub {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.fb-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--text-body);
}
.fb-actions { margin-bottom: 0; }

/* 星级用站点的 warning 令牌，暗色主题下自动跟着换色；悬停不放大 */
.feedback-page :deep(.el-rate) { --el-rate-fill-color: var(--warning); }
.feedback-page :deep(.el-rate__icon.hover) { transform: none; }

.fb-history { margin-top: 40px; }
.fb-section-title {
  margin: 0 0 20px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.3;
}
.fb-history :deep(.el-timeline) { padding: 0; }

.feedback-item {
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}

.feedback-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.feedback-item-header h3 {
  margin: 0;
  min-width: 0;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.feedback-content {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.feedback-reply {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
}
.reply-label {
  margin: 0 0 4px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.reply-body {
  margin: 0 0 6px;
  color: var(--text-body);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.reply-time {
  font-size: 12px;
  color: var(--text-muted);
}

@media (max-width: 640px) {
  .feedback-page { padding: 24px 16px 48px; }
  .fb-head h1 { font-size: 24px; }
  /* 四个单选在 390px 宽放不下一行，缩小间距让它们两两换行 */
  .feedback-page :deep(.el-radio) { margin-right: 20px; }
  .feedback-item { padding: 12px; }
  .feedback-item-header { align-items: flex-start; }
}
</style>
