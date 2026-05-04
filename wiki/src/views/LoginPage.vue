<template>
  <div class="auth-page-container">
    <el-card class="auth-card">
      <template #header>
        <div class="card-header">
          <span>{{ formTitle }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="邮箱" prop="userEmail">
          <el-input
            v-model="form.userEmail"
            placeholder="请输入学校邮箱地址"
            clearable
          />
        </el-form-item>

        <el-form-item v-if="mode !== 'forgot'" label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item v-if="mode === 'register' || mode === 'forgot'" label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <el-input
            v-model="form.code"
            placeholder="请输入验证码"
            clearable
            style="width: calc(100% - 110px); margin-right: 10px"
          />
          <el-button
            type="primary"
            @click="handleSendCode"
            :disabled="isSendingCode || sendCodeDisabled"
            style="width: 100px"
          >
            {{ sendCodeButtonText }}
          </el-button>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleSubmit"
            :loading="isLoading"
            class="submit-button"
          >
            {{ submitButtonText }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="form-footer">
        <el-link v-if="mode !== 'login'" type="primary" @click="switchMode('login')">
          已有账号？立即登录
        </el-link>
        <el-link v-if="mode !== 'register'" type="primary" @click="switchMode('register')">
          没有账号？立即注册
        </el-link>
        <el-link v-if="mode !== 'forgot'" type="primary" @click="switchMode('forgot')">
          忘记密码？
        </el-link>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, computed, onUnmounted } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { useRouter } from 'vue-router'
import { login, register, resetPassword, sendCode } from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

export default {
  name: 'AuthPage',
  setup() {
    const formRef = ref(null)
    const router = useRouter()
    const userStore = useUserStore()
    const mode = ref('login')

    const form = reactive({
      userEmail: '',
      password: '',
      confirmPassword: '',
      code: ''
    })

    const validateConfirmPassword = (rule, value, callback) => {
      if (mode.value === 'login') {
        callback()
        return
      }
      if (value !== form.password) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }

    const rules = computed(() => ({
      userEmail: [
        { required: true, message: '请输入邮箱地址', trigger: 'blur' },
        { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] }
      ],
      password: [
        { required: mode.value !== 'forgot', message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 20, message: '密码长度应为6-20位', trigger: 'blur' }
      ],
      confirmPassword: [
        { required: mode.value !== 'login', message: '请确认密码', trigger: 'blur' },
        { validator: validateConfirmPassword, trigger: 'blur' }
      ],
      code: [
        { required: true, message: '请输入验证码', trigger: 'blur' },
        { min: 4, max: 6, message: '验证码长度应为4-6位', trigger: 'blur' }
      ]
    }))

    const formTitle = computed(() => {
      const titles = { login: '用户登录', register: '用户注册', forgot: '重置密码' }
      return titles[mode.value] || '用户登录'
    })

    const submitButtonText = computed(() => {
      const texts = { login: '登录', register: '注册', forgot: '重置密码' }
      return texts[mode.value] || '登录'
    })

    const isLoading = ref(false)
    const isSendingCode = ref(false)
    const countdown = ref(0)
    const sendCodeTimer = ref(null)

    const sendCodeDisabled = computed(() => countdown.value > 0)
    const sendCodeButtonText = computed(() => {
      if (isSendingCode.value) return '发送中...'
      if (countdown.value > 0) return `${countdown.value}s 后重试`
      return '获取验证码'
    })

    const codeType = computed(() => {
      const types = { login: 'login', register: 'register', forgot: 'reset' }
      return types[mode.value] || 'login'
    })

    const validateEmail = async () => {
      if (!formRef.value) return false
      try {
        await formRef.value.validateField('userEmail')
        return true
      } catch {
        return false
      }
    }

    const handleSendCode = async () => {
      const isEmailValid = await validateEmail()
      if (!isEmailValid) {
        ElMessage.error('请输入有效的邮箱地址')
        return
      }
      if (sendCodeDisabled.value || isSendingCode.value) return

      isSendingCode.value = true
      sendCode(
        form.userEmail,
        codeType.value,
        () => {
          ElNotification({
            title: '成功',
            message: '验证码已发送至您的邮箱，请注意查收。',
            type: 'success'
          })
          countdown.value = 60
          sendCodeTimer.value = setInterval(() => {
            if (countdown.value > 0) {
              countdown.value--
            } else {
              clearInterval(sendCodeTimer.value)
              sendCodeTimer.value = null
            }
          }, 1000)
          isSendingCode.value = false
        },
        (message) => {
          ElMessage.error(message || '发送验证码失败，请稍后重试。')
          isSendingCode.value = false
        }
      )
    }

    const handleSubmit = async () => {
      if (!formRef.value) return
      formRef.value.validate((valid) => {
        if (!valid) {
          ElMessage.error('请检查表单输入是否正确。')
          return
        }
        isLoading.value = true

        const onSuccess = (data) => {
          isLoading.value = false
          if (data && data.userInfo) {
            userStore.setUserInfo(data.userInfo)
          }
          ElNotification({
            title: '成功',
            message: mode.value === 'login' ? '欢迎回来！' : '操作成功！',
            type: 'success'
          })
          router.push('/')
        }

        const onFailure = (message) => {
          isLoading.value = false
          ElMessage.error(message || '操作失败，请稍后重试。')
        }

        if (mode.value === 'login') {
          login(form.userEmail, form.password, onSuccess, onFailure)
        } else if (mode.value === 'register') {
          register(form.userEmail, form.password, form.code, onSuccess, onFailure)
        } else if (mode.value === 'forgot') {
          resetPassword(form.userEmail, form.password, form.code, onSuccess, onFailure)
        }
      })
    }

    const switchMode = (newMode) => {
      mode.value = newMode
      form.password = ''
      form.confirmPassword = ''
      form.code = ''
      if (formRef.value) {
        formRef.value.clearValidate()
      }
    }

    onUnmounted(() => {
      if (sendCodeTimer.value) {
        clearInterval(sendCodeTimer.value)
      }
    })

    return {
      formRef,
      mode,
      form,
      rules,
      formTitle,
      submitButtonText,
      isLoading,
      isSendingCode,
      sendCodeDisabled,
      sendCodeButtonText,
      handleSendCode,
      handleSubmit,
      switchMode
    }
  }
}
</script>

<style scoped>
.auth-page-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 60px);
  background-color: #f0f2f5;
  padding: 20px;
}

.auth-card {
  width: 100%;
  max-width: 440px;
}

.card-header {
  text-align: center;
  font-size: 1.5rem;
  font-weight: bold;
}

.submit-button {
  width: 100%;
}

.form-footer {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 8px;
  flex-wrap: wrap;
}
</style>
