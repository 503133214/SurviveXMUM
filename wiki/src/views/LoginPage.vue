<template>
  <div class="login-page-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span>用户登录</span>
        </div>
      </template>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="80px"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="邮箱" prop="userEmail">
          <el-input
            v-model="loginForm.userEmail"
            placeholder="请输入邮箱地址"
            clearable
          />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <el-input
            v-model="loginForm.code"
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
            @click="handleLogin"
            :loading="isLoading"
            class="login-button"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
<script>
import { ref, reactive, computed, onUnmounted } from 'vue'; // 确保导入 onUnmounted
import { ElMessage, ElNotification } from 'element-plus';
import axios from 'axios';
import { useRouter } from 'vue-router';
import { login } from '@/net/index.js';
// import { useStore } from 'vuex'; // 如果你使用 Vuex 管理认证状态

export default {
  name: 'LoginPage',
  setup() {
    const loginFormRef = ref(null);
    const router = useRouter();
    // const store = useStore(); // 如果使用 Vuex

    const loginForm = reactive({
      userEmail: '',
      code: '',
    });

    const loginRules = {
      userEmail: [
        { required: true, message: '请输入邮箱地址', trigger: 'blur' },
        { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] },
      ],
      code: [
        { required: true, message: '请输入验证码', trigger: 'blur' },
        // 根据你的后端调整验证码长度，通常为4-6位
        { min: 4, max: 6, message: '验证码错误', trigger: 'blur' },
      ],
    };

    const isLoading = ref(false);
    const isSendingCode = ref(false);
    const countdown = ref(0);
    const sendCodeTimer = ref(null);

    const sendCodeDisabled = computed(() => countdown.value > 0);
    const sendCodeButtonText = computed(() => {
      if (isSendingCode.value) return '发送中...';
      if (countdown.value > 0) return `${countdown.value}s 后重试`;
      return '获取验证码';
    });

    // 验证邮箱字段的有效性
    const validateEmail = async () => {
      if (!loginFormRef.value) return false;
      try {
        await loginFormRef.value.validateField('userEmail');
        return true;
      } catch (error) {
        return false;
      }
    };

    const handleSendCode = async () => {
      const isEmailValid = await validateEmail();
      if (!isEmailValid) {
         ElMessage.error('请输入有效的邮箱地址');
        return;
      }
      if (sendCodeDisabled.value || isSendingCode.value) return;

      isSendingCode.value = true;
      try {
        // 根据文档: /login/send/code POST, email 作为查询参数
        const response = await axios.post(`/login/send/code?email=${encodeURIComponent(loginForm.userEmail)}`);
        // 为 axios 配置了 baseURL
        if (response.data && response.data.code === 0) { // 根据你的 API 成功码调整
          ElNotification({
            title: '成功',
            message: '验证码已发送至您的邮箱，请注意查收。',
            type: 'success',
          });
          countdown.value = 60; // 开始 60 秒倒计时
          sendCodeTimer.value = setInterval(() => {
            if (countdown.value > 0) {
              countdown.value--;
            } else {
              clearInterval(sendCodeTimer.value);
              sendCodeTimer.value = null; // 清除后置为null
            }
          }, 1000);
        } else {
          ElMessage.error(response.data.message || '发送验证码失败，请稍后重试。');
        }
      } catch (error) {
        console.error('发送验证码错误:', error);
        ElMessage.error('发送验证码请求失败，请检查网络或联系管理员。');
      } finally {
        isSendingCode.value = false;
      }
    };

    const handleLogin = async () => {
      if (!loginFormRef.value) return;
      
      loginFormRef.value.validate((valid) => {
        if (valid) {
          isLoading.value = true;
          login(
            loginForm.userEmail,  // username
            loginForm.code,       // code
            (data) => {          // success 回调
              isLoading.value = false;
              ElNotification({
                title: '登录成功',
                message: '欢迎回来！',
                type: 'success',
              });
              router.push('/'); // 跳转到主页
            },
            (message, code, url) => {
              isLoading.value = false;
              ElMessage.error(message || '登录失败，邮箱或验证码错误。');
            }
          );
        } else {
          ElMessage.error('请检查表单输入是否正确。');
        }
      });
    };

    // 组件卸载时清除定时器
    onUnmounted(() => {
      if (sendCodeTimer.value) {
        clearInterval(sendCodeTimer.value);
      }
    });

    return {
      loginFormRef,
      loginForm,
      loginRules,
      isLoading,
      isSendingCode,
      sendCodeDisabled,
      sendCodeButtonText,
      handleSendCode,
      handleLogin,
    };
  },
};
</script>

<style scoped>
.login-page-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - var(--header-height, 60px)); /* 假设你有一个页头，并定义了 --header-height CSS 变量 */
  background-color: #f0f2f5;
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
}

.card-header {
  text-align: center;
  font-size: 1.5rem;
  font-weight: bold;
}

.login-button {
  width: 100%;
}
</style>

