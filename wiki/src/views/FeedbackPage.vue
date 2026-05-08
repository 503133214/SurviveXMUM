<template>
  <div class="feedback-page">
    <el-card class="feedback-card">
      <template #header>
        <div class="card-header">
          <span>系统反馈</span>
        </div>
      </template>

      <el-alert
        title="我们重视您的每一条反馈"
        description="如果您在使用过程中遇到任何问题，或对系统功能、界面、交互有任何建议，欢迎在此提交。您的反馈将帮助我们持续优化产品体验。"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 24px;"
      />

      <el-form
        ref="feedbackFormRef"
        :model="feedbackForm"
        :rules="feedbackRules"
        label-width="100px"
      >
        <el-form-item label="反馈类型" prop="type">
          <el-radio-group v-model="feedbackForm.type">
            <el-radio label="bug">问题反馈</el-radio>
            <el-radio label="feature">功能建议</el-radio>
            <el-radio label="ui">界面优化</el-radio>
            <el-radio label="other">其他</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="反馈标题" prop="title">
          <el-input
            v-model="feedbackForm.title"
            placeholder="请简要描述反馈主题"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="详细描述" prop="content">
          <el-input
            v-model="feedbackForm.content"
            type="textarea"
            :rows="6"
            placeholder="请详细描述您遇到的问题或建议..."
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="满意度" prop="rating">
          <el-rate
            v-model="feedbackForm.rating"
            :colors="['#99A9BF', '#F7BA2A', '#FF9900']"
            show-text
            :texts="['非常不满意', '不满意', '一般', '满意', '非常满意']"
          />
        </el-form-item>

        <el-form-item label="联系方式" prop="contact">
          <el-input
            v-model="feedbackForm.contact"
            placeholder="选填，便于我们与您沟通（邮箱/手机号）"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submitFeedback" :loading="isSubmitting">
            提交反馈
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="myFeedbacks.length" class="feedback-list-card">
      <template #header>
        <div class="list-header">
          <span>我的反馈记录</span>
        </div>
      </template>

      <el-timeline>
        <el-timeline-item
          v-for="item in myFeedbacks"
          :key="item.id"
          :timestamp="item.createTime"
          :type="item.status === 'resolved' ? 'success' : item.status === 'processing' ? 'warning' : 'info'"
        >
          <el-card class="feedback-item" shadow="hover">
            <div class="feedback-item-header">
              <h4>{{ item.title }}</h4>
              <el-tag :type="statusType(item.status)">{{ statusText(item.status) }}</el-tag>
            </div>
            <p class="feedback-content">{{ item.content }}</p>
            <div v-if="item.reply" class="feedback-reply">
              <el-divider content-position="left">官方回复</el-divider>
              <p>{{ item.reply }}</p>
              <span class="reply-time">回复于 {{ item.replyTime }}</span>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-card>
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
            ElMessage.success('反馈提交成功，感谢您的宝贵意见！')
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
.feedback-page {
  max-width: 800px;
  margin: 20px auto;
  padding: 0 20px;
}

.feedback-card {
  margin-bottom: 24px;
}

.card-header,
.list-header {
  font-size: 1.25rem;
  font-weight: bold;
}

.feedback-list-card {
  margin-bottom: 40px;
}

.feedback-item {
  margin-bottom: 8px;
}

.feedback-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.feedback-item-header h4 {
  margin: 0;
}

.feedback-content {
  margin: 0;
  color: #666;
  line-height: 1.6;
  white-space: pre-wrap;
}

.feedback-reply {
  margin-top: 16px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

.feedback-reply p {
  margin: 0 0 8px;
  color: #333;
  line-height: 1.6;
}

.reply-time {
  font-size: 0.85rem;
  color: #999;
}
</style>
