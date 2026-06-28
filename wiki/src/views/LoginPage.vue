<template>
  <div class="auth-page">
    <div class="auth-card fade-up">
      <div class="auth-head">
        <img src="/svg/Simple_Logo.svg" alt="XMUM Wiki" class="auth-logo" />
        <h1 class="auth-title">{{ formTitle }}</h1>
        <p class="auth-sub">SurviveXMUM · 厦马生存手册</p>
      </div>

      <!-- 模式切换 -->
      <div class="seg" v-if="mode !== 'forgot'">
        <button :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :validate-on-rule-change="false"
        label-position="top"
        @submit.prevent="handleSubmit"
        class="auth-form"
      >
        <el-form-item label="校园邮箱" prop="userEmail">
          <el-input v-model="form.userEmail" placeholder="yourname@xmu.edu.my" clearable size="large" />
        </el-form-item>

        <el-form-item v-if="mode !== 'forgot'" label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password clearable size="large" />
        </el-form-item>

        <el-form-item v-if="mode === 'forgot'" label="新密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入新密码" show-password clearable size="large" />
        </el-form-item>

        <el-form-item v-if="mode === 'register' || mode === 'forgot'" label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" show-password clearable size="large" />
        </el-form-item>

        <el-form-item v-if="mode !== 'login'" label="邮箱验证码" prop="code">
          <div class="code-row">
            <el-input v-model="form.code" placeholder="6 位验证码" clearable size="large" />
            <button
              type="button"
              class="code-btn"
              @click="handleSendCode"
              :disabled="isSendingCode || sendCodeDisabled"
            >{{ sendCodeButtonText }}</button>
          </div>
        </el-form-item>

        <button type="button" class="submit-btn" :class="{ loading: isLoading }" :disabled="isLoading" @click="handleSubmit">
          {{ isLoading ? '处理中…' : submitButtonText }}
        </button>
      </el-form>

      <div v-if="mode !== 'register'" class="auth-foot">
        <a v-if="mode === 'login'" @click="switchMode('forgot')">忘记密码？</a>
        <a v-else @click="switchMode('login')">← 返回登录</a>
      </div>

      <p class="auth-note" v-if="mode === 'register'">
        仅支持厦门大学马来西亚分校校园邮箱（@xmu.edu.my）注册。
      </p>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, nextTick, onUnmounted } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { login, register, resetPassword, sendCode } from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

export default {
  name: 'AuthPage',
  setup() {
    const formRef = ref(null)
    const router = useRouter()
    const route = useRoute()
    const userStore = useUserStore()
    const mode = ref('login')

    const form = reactive({ userEmail: '', password: '', confirmPassword: '', code: '' })

    const validateConfirmPassword = (rule, value, callback) => {
      if (mode.value === 'login') return callback()
      if (value !== form.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    }

    const rules = computed(() => ({
      userEmail: [
        { required: true, message: '请输入邮箱地址', trigger: 'blur' },
        { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] },
      ],
      password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 20, message: '密码长度应为6-20位', trigger: 'blur' },
      ],
      confirmPassword: [
        { required: mode.value !== 'login', message: '请确认密码', trigger: 'blur' },
        { validator: validateConfirmPassword, trigger: 'blur' },
      ],
      code: [
        { required: mode.value !== 'login', message: '请输入验证码', trigger: 'blur' },
        { min: 4, max: 6, message: '验证码长度应为4-6位', trigger: 'blur' },
      ],
    }))

    const formTitle = computed(() => ({ login: '欢迎回来', register: '创建账号', forgot: '重置密码' }[mode.value] || '登录'))
    const submitButtonText = computed(() => ({ login: '登录', register: '注册', forgot: '重置密码' }[mode.value] || '登录'))

    const isLoading = ref(false)
    const isSendingCode = ref(false)
    const countdown = ref(0)
    const sendCodeTimer = ref(null)

    const sendCodeDisabled = computed(() => countdown.value > 0)
    const sendCodeButtonText = computed(() => {
      if (isSendingCode.value) return '发送中…'
      if (countdown.value > 0) return `${countdown.value}s`
      return '获取验证码'
    })
    const codeType = computed(() => ({ register: 'register', forgot: 'reset' }[mode.value] || 'register'))

    const validateEmail = async () => {
      if (!formRef.value) return false
      try { await formRef.value.validateField('userEmail'); return true } catch { return false }
    }

    const handleSendCode = async () => {
      if (!(await validateEmail())) { ElMessage.error('请输入有效的邮箱地址'); return }
      if (sendCodeDisabled.value || isSendingCode.value) return
      isSendingCode.value = true
      sendCode(form.userEmail, codeType.value,
        () => {
          ElNotification({ title: '已发送', message: '验证码已发送至邮箱，请查收。', type: 'success' })
          countdown.value = 60
          sendCodeTimer.value = setInterval(() => {
            if (countdown.value > 0) countdown.value--
            else { clearInterval(sendCodeTimer.value); sendCodeTimer.value = null }
          }, 1000)
          isSendingCode.value = false
        },
        (message) => { ElMessage.error(message || '发送失败，请稍后重试。'); isSendingCode.value = false })
    }

    const handleSubmit = async () => {
      if (!formRef.value) return
      formRef.value.validate((valid) => {
        if (!valid) { ElMessage.error('请检查表单输入'); return }
        isLoading.value = true
        const onSuccess = async (data) => {
          if (data && data.userInfo) userStore.setUserInfo(data.userInfo)
          else await userStore.fetchUserInfo()
          isLoading.value = false
          if (mode.value === 'forgot') {
            ElMessage.success('密码已重置，请登录')
            switchMode('login')
            return
          }
          ElNotification({ title: '成功', message: '欢迎使用 SurviveXMUM Wiki！', type: 'success' })
          const redirect = route.query.redirect
          router.push(typeof redirect === 'string' ? redirect : '/')
        }
        const onFailure = (message) => { isLoading.value = false; ElMessage.error(message || '操作失败') }

        if (mode.value === 'login') login(form.userEmail, form.password, onSuccess, onFailure)
        else if (mode.value === 'register') register(form.userEmail, form.password, form.code, onSuccess, onFailure)
        else if (mode.value === 'forgot') resetPassword(form.userEmail, form.password, form.code, onSuccess, onFailure)
      })
    }

    const switchMode = async (newMode) => {
      mode.value = newMode
      form.password = ''
      form.confirmPassword = ''
      form.code = ''
      await nextTick()
      if (formRef.value) formRef.value.clearValidate()
    }

    onUnmounted(() => { if (sendCodeTimer.value) clearInterval(sendCodeTimer.value) })

    return {
      formRef, mode, form, rules, formTitle, submitButtonText,
      isLoading, isSendingCode, sendCodeDisabled, sendCodeButtonText,
      handleSendCode, handleSubmit, switchMode,
    }
  },
}
</script>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: calc(100vh - var(--header-height));
  padding: 64px 20px;
  background:
    radial-gradient(60% 50% at 50% 0%, var(--bg-subtle) 0%, transparent 70%),
    var(--bg-page);
}
.auth-card {
  width: 100%;
  max-width: 420px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 40px 36px;
  box-shadow: var(--shadow-sm);
}
.auth-head { text-align: center; margin-bottom: 24px; }
.auth-logo { height: 44px; width: auto; margin-bottom: 18px; }
html.dark .auth-logo { filter: brightness(0) invert(1); }
.auth-title {
  font-size: 1.6rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  margin: 0;
}
.auth-sub { color: var(--text-muted); font-size: 13.5px; margin: 6px 0 0; }

.seg {
  display: flex;
  background: var(--bg-subtle);
  border-radius: 999px;
  padding: 4px;
  margin-bottom: 22px;
}
.seg button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 9px 0;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-secondary);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s var(--ease-out);
}
.seg button.active {
  background: var(--bg-surface);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.auth-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--text-body);
  padding-bottom: 4px;
}
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row .el-input { flex: 1; }
.code-btn {
  flex-shrink: 0;
  white-space: nowrap;
  padding: 0 16px;
  border: 1px solid var(--border-strong);
  background: var(--bg-surface);
  color: var(--text-primary);
  border-radius: var(--radius-sm);
  font-weight: 600;
  font-size: 13.5px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.code-btn:hover:not(:disabled) { background: var(--bg-hover); }
.code-btn:disabled { color: var(--text-muted); cursor: not-allowed; }

.submit-btn {
  width: 100%;
  margin-top: 6px;
  padding: 13px 0;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.1s ease;
}
.submit-btn:hover:not(:disabled) { opacity: 0.88; }
.submit-btn:active:not(:disabled) { transform: scale(0.99); }
.submit-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.auth-foot { text-align: center; margin-top: 18px; }
.auth-foot a {
  color: var(--text-secondary);
  font-size: 13.5px;
  cursor: pointer;
}
.auth-foot a:hover { color: var(--text-primary); text-decoration: underline; }
.auth-note {
  margin-top: 16px;
  font-size: 12.5px;
  color: var(--text-muted);
  text-align: center;
  line-height: 1.6;
}
</style>
