<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-head">
        <img src="/svg/Simple_Logo.svg" alt="XMUM Wiki" class="auth-logo" />
        <h1 class="auth-title">{{ formTitle }}</h1>
        <p class="auth-sub">SurviveXMUM · 厦马生存手册</p>
      </div>

      <!-- 模式切换 -->
      <div class="seg" v-if="mode !== 'forgot'">
        <button type="button" :class="{ active: mode === 'login' }" :aria-pressed="mode === 'login'" @click="switchMode('login')">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" :aria-pressed="mode === 'register'" @click="switchMode('register')">注册</button>
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

      <!-- 用 button 而不是无 href 的 <a>：后者拿不到键盘焦点，Tab 键用户点不到 -->
      <div v-if="mode !== 'register'" class="auth-foot">
        <button v-if="mode === 'login'" type="button" class="link-btn" @click="switchMode('forgot')">忘记密码？</button>
        <button v-else type="button" class="link-btn" @click="switchMode('login')">← 返回登录</button>
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

    // 标题直接写当前要做的事，和提交按钮同一套字，不用「欢迎回来」这类客套话
    const formTitle = computed(() => ({ login: '登录', register: '注册', forgot: '重置密码' }[mode.value] || '登录'))
    const submitButtonText = formTitle

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
          ElMessage.success(mode.value === 'register' ? '注册成功' : '登录成功')
          // 登录后回到原页面；只接受单个 / 开头的站内路径，防止 redirect 被指向站外地址
          const redirect = route.query.redirect
          const safeRedirect = typeof redirect === 'string'
            && redirect.startsWith('/')
            && !redirect.startsWith('//')
            && !redirect.startsWith('/\\')
            ? redirect : '/'
          router.push(safeRedirect)
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
/* 扁平登录页：纯色底、只用 1px 描边分隔，不做入场动画和阴影 */
.auth-page {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: calc(100vh - var(--header-height));
  padding: 64px 20px;
  background: var(--bg-page);
}
.auth-card {
  width: 100%;
  max-width: 420px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 40px 36px;
}
.auth-head { text-align: center; margin-bottom: 24px; }
.auth-logo { height: 44px; width: auto; margin-bottom: 18px; }
html.dark .auth-logo { filter: brightness(0) invert(1); }
.auth-title {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
  color: var(--text-primary);
  margin: 0;
}
.auth-sub { color: var(--text-muted); font-size: 13px; margin: 6px 0 0; }

/* 登录 / 注册切换：矩形分段控件，选中项只靠底色和 1px 描边区分 */
.seg {
  display: flex;
  background: var(--bg-subtle);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 3px;
  margin-bottom: 22px;
}
.seg button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 7px 0;
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: background var(--dur), color var(--dur);
}
.seg button:hover:not(.active) { color: var(--text-primary); }
.seg button.active {
  background: var(--bg-surface);
  color: var(--text-primary);
  box-shadow: 0 0 0 1px var(--border);
}

.auth-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--text-body);
  padding-bottom: 4px;
}
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row .el-input { flex: 1; min-width: 0; }
.code-btn {
  flex-shrink: 0;
  white-space: nowrap;
  padding: 0 16px;
  border: 1px solid var(--border-strong);
  background: var(--bg-surface);
  color: var(--text-primary);
  border-radius: var(--radius-sm);
  font: inherit;
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  transition: background var(--dur), border-color var(--dur), color var(--dur);
}
.code-btn:hover:not(:disabled) { background: var(--bg-hover); }
.code-btn:disabled { color: var(--text-muted); cursor: not-allowed; }

.submit-btn {
  width: 100%;
  height: 40px;
  margin-top: 6px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--accent);
  color: var(--accent-contrast);
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--dur);
}
.submit-btn:hover:not(:disabled) { background: var(--accent-hover); }
.submit-btn:disabled { opacity: 0.55; cursor: not-allowed; }

.auth-foot { text-align: center; margin-top: 18px; }
.link-btn {
  padding: 2px 4px;
  border: none;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: color var(--dur);
}
.link-btn:hover { color: var(--text-primary); text-decoration: underline; }
.auth-note {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  line-height: 1.6;
}

@media (max-width: 480px) {
  .auth-page { min-height: calc(100dvh - var(--header-height)); padding: 32px 16px 48px; }
  .auth-card { padding: 30px 18px; }
  .auth-logo { height: 38px; margin-bottom: 14px; }
  .code-row { gap: 8px; }
  .code-btn { padding-inline: 12px; font-size: 13px; }
}
</style>
